package ge.kiu.loanplatform.service.document;

import ge.kiu.loanplatform.dto.document.DocumentTypeRequest;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.document.DocumentRepository;
import ge.kiu.loanplatform.repository.document.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.product.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Document types are the kinds of files a product can require, e.g. "ID Card"
@Service
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;
    private final LoanProductRepository loanProductRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> getAll() {
        return documentTypeRepository.findAll(Sort.by("name")).stream()
                .map(DocumentTypeResponse::from)
                .toList();
    }

    @Transactional
    public DocumentTypeResponse create(DocumentTypeRequest request) {
        // IgnoreCase: "ID Card" and "id card" would be the same type for a person
        if (documentTypeRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Document type with name " + request.name() + " already exists");
        }

        DocumentType documentType = new DocumentType();
        documentType.setName(request.name());

        DocumentType saved = documentTypeRepository.save(documentType);
        return DocumentTypeResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        DocumentType documentType = documentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document type with id " + id + " not found"));

        // a type that is still used cannot disappear: the rows that point to it would be broken
        if (loanProductRepository.existsByRequiredDocumentsId(id)) {
            throw new ConflictException("Document type with id " + id + " is required by a loan product and cannot be deleted");
        }
        if (documentRepository.existsByDocumentTypeId(id)) {
            throw new ConflictException("Document type with id " + id + " is used by uploaded documents and cannot be deleted");
        }
        documentTypeRepository.delete(documentType);
    }
}
