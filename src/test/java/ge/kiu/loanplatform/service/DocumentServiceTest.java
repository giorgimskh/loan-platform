package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.DocumentFile;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.entity.Document;
import ge.kiu.loanplatform.entity.LoanApplication;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.DocumentRepository;
import ge.kiu.loanplatform.repository.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

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
                .isInstanceOf(ConflictException.class);
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
