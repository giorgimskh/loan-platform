package ge.kiu.loanplatform.service.document;

import ge.kiu.loanplatform.dto.document.DocumentFile;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.document.Document;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.exception.custom.ErrorCode;
import ge.kiu.loanplatform.exception.custom.InvalidStatusException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.application.LoanApplicationRepository;
import ge.kiu.loanplatform.repository.document.DocumentRepository;
import ge.kiu.loanplatform.repository.document.DocumentTypeRepository;
import ge.kiu.loanplatform.service.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: no Spring, no database, no real files. Repositories and file storage are Mockito mocks.
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void upload_keepsOnlyTheFileNamePartOfTheOriginalName() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(new LoanApplication()));
        when(documentTypeRepository.findById(2L)).thenReturn(Optional.of(new DocumentType()));
        when(fileStorageService.store(any())).thenReturn("uploads/0b6f.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

        // folders in the name (also Windows ones) are dropped before the name is saved
        MockMultipartFile file = new MockMultipartFile("file", "..\\..\\docs/id-card.pdf", "application/pdf", "x".getBytes());
        documentService.upload(1L, file, 2L);

        verify(documentRepository).save(org.mockito.ArgumentMatchers.argThat(document ->
                document.getOriginalFileName().equals("id-card.pdf")
                        && document.getStoredFilePath().equals("uploads/0b6f.pdf")));
    }

    @Test
    void download_returnsTheStoredFileWithItsOriginalName() {
        Resource storedFile = new ByteArrayResource("fake pdf content".getBytes());
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document(ApplicationStatus.APPROVED)));
        when(fileStorageService.load("uploads/abc.pdf")).thenReturn(storedFile);

        DocumentFile file = documentService.download(10L);

        assertThat(file.originalFileName()).isEqualTo("id-card.pdf");
        assertThat(file.resource()).isSameAs(storedFile);
    }

    @Test
    void download_throwsNotFound_whenDocumentDoesNotExist() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.download(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesTheFileAndTheRow_whenApplicationIsSubmitted() {
        Document document = document(ApplicationStatus.SUBMITTED);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

        documentService.delete(10L);

        verify(fileStorageService).delete("uploads/abc.pdf");
        verify(documentRepository).delete(document);
    }

    @Test
    void delete_throwsConflict_andKeepsTheFile_whenApplicationIsAlreadyDecided() {
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document(ApplicationStatus.APPROVED)));

        assertThatThrownBy(() -> documentService.delete(10L))
                .isInstanceOf(InvalidStatusException.class)
                .hasMessage("Document with id 10 belongs to an application that is APPROVED and cannot be deleted. An APPROVED application can only be rejected or have its decision undone.")
                .extracting("code").isEqualTo(ErrorCode.APPLICATION_NOT_MODIFIABLE);
        verify(fileStorageService, never()).delete(any());
        verify(documentRepository, never()).delete(any());
    }

    // a document that belongs to an application with the given status
    private Document document(ApplicationStatus applicationStatus) {
        LoanApplication application = new LoanApplication();
        application.setId(1L);
        application.setStatus(applicationStatus);

        Document document = new Document();
        document.setId(10L);
        document.setOriginalFileName("id-card.pdf");
        document.setStoredFilePath("uploads/abc.pdf");
        document.setLoanApplication(application);
        return document;
    }
}
