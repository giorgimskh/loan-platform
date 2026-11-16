package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.DocumentTypeResponse;
import ge.kiu.loanplatform.entity.DocumentType;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.DocumentRepository;
import ge.kiu.loanplatform.repository.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.LoanProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: no Spring, no database. The repositories are replaced by Mockito mocks.
@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private LoanProductRepository loanProductRepository;
    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentTypeService documentTypeService;

    @Test
    void create_savesTheType_whenNameIsFree() {
        when(documentTypeRepository.existsByNameIgnoreCase("Passport")).thenReturn(false);
        when(documentTypeRepository.save(any(DocumentType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentTypeResponse response = documentTypeService.create(new DocumentTypeRequest("Passport"));

        assertThat(response.name()).isEqualTo("Passport");
    }

    @Test
    void create_throwsConflict_whenNameAlreadyExists() {
        when(documentTypeRepository.existsByNameIgnoreCase("id card")).thenReturn(true);

        assertThatThrownBy(() -> documentTypeService.create(new DocumentTypeRequest("id card")))
                .isInstanceOf(ConflictException.class);
        verify(documentTypeRepository, never()).save(any());
    }

    @Test
    void delete_removesTheType_whenNothingUsesIt() {
        DocumentType passport = documentType();
        when(documentTypeRepository.findById(2L)).thenReturn(Optional.of(passport));

        documentTypeService.delete(2L);

        verify(documentTypeRepository).delete(passport);
    }

    @Test
    void delete_throwsConflict_whenAProductRequiresTheType() {
        when(documentTypeRepository.findById(2L)).thenReturn(Optional.of(documentType()));
        when(loanProductRepository.existsByRequiredDocumentsId(2L)).thenReturn(true);

        assertThatThrownBy(() -> documentTypeService.delete(2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("loan product");
        verify(documentTypeRepository, never()).delete(any());
    }

    @Test
    void delete_throwsConflict_whenADocumentWasUploadedWithTheType() {
        when(documentTypeRepository.findById(2L)).thenReturn(Optional.of(documentType()));
        when(documentRepository.existsByDocumentTypeId(2L)).thenReturn(true);

        assertThatThrownBy(() -> documentTypeService.delete(2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("uploaded documents");
        verify(documentTypeRepository, never()).delete(any());
    }

    @Test
    void delete_throwsNotFound_whenTypeDoesNotExist() {
        when(documentTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentTypeService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private DocumentType documentType() {
        DocumentType documentType = new DocumentType();
        documentType.setId(2L);
        documentType.setName("Passport");
        return documentType;
    }
}
