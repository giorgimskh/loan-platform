package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.DocumentFile;
import ge.kiu.loanplatform.dto.DocumentResponse;
import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.entity.Document;
import ge.kiu.loanplatform.entity.DocumentType;
import ge.kiu.loanplatform.entity.LoanApplication;
import ge.kiu.loanplatform.exception.ConflictException;
import ge.kiu.loanplatform.exception.ResourceNotFoundException;
import ge.kiu.loanplatform.repository.DocumentRepository;
import ge.kiu.loanplatform.repository.DocumentTypeRepository;
import ge.kiu.loanplatform.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public DocumentResponse upload(Long applicationId, MultipartFile file, Long documentTypeId) {
        // look up both ids first, so nothing is written to disk for a request that will fail with 404
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application with id " + applicationId + " not found"));
        DocumentType documentType = documentTypeRepository.findById(documentTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Document type with id " + documentTypeId + " not found"));

        String storedFilePath = fileStorageService.store(file);

        Document document = new Document();
        document.setOriginalFileName(file.getOriginalFilename());
        document.setStoredFilePath(storedFilePath);
        document.setLoanApplication(application);
        document.setDocumentType(documentType);

        Document saved = documentRepository.save(document);
        return DocumentResponse.from(saved);
    }

    // The stored file of a document, with the name the customer gave it
    @Transactional(readOnly = true)
    public DocumentFile download(Long id) {
        Document document = findDocument(id);
        return new DocumentFile(document.getOriginalFileName(), fileStorageService.load(document.getStoredFilePath()));
    }

    @Transactional
    public void delete(Long id) {
        Document document = findDocument(id);

        // After a decision the documents are the proof it was based on, so they must stay
        LoanApplication application = document.getLoanApplication();
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictException("Document with id " + id + " belongs to an application that is "
                    + application.getStatus() + " and cannot be deleted");
        }

        fileStorageService.delete(document.getStoredFilePath()); // the file on disk
        documentRepository.delete(document);                     // the row in the database
    }

    // ---------- helpers ----------

    private Document findDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document with id " + id + " not found"));
    }
}
