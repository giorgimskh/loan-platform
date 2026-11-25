package ge.kiu.loanplatform.service.document;

import ge.kiu.loanplatform.dto.document.DocumentFile;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
        document.setOriginalFileName(displayName(file.getOriginalFilename()));
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
        // State pattern: the status itself knows whether the application can still be changed
        if (!application.getStatus().canBeModified()) {
            throw new InvalidStatusException(ErrorCode.APPLICATION_NOT_MODIFIABLE, application.getStatus(),
                    "Document with id " + id + " belongs to an application that is "
                            + application.getStatus() + " and cannot be deleted.");
        }

        fileStorageService.delete(document.getStoredFilePath()); // the file on disk
        documentRepository.delete(document);                     // the row in the database
    }

    // ---------- helpers ----------

    // The original name is only shown and used as the download name, never as a path on disk.
    // A client can still send any text as the name, e.g. "../../secret.pdf" or "C:\docs\id.pdf":
    // cleanPath turns "\\" into "/", getFilename keeps only the part after the last "/".
    // The column holds at most 255 characters, so a longer name keeps its last 255 (with the extension).
    private String displayName(String originalFileName) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(originalFileName));
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private Document findDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document with id " + id + " not found"));
    }
}
