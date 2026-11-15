package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.DocumentResponse;
import ge.kiu.loanplatform.entity.Document;
import ge.kiu.loanplatform.entity.DocumentType;
import ge.kiu.loanplatform.entity.LoanApplication;
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
}
