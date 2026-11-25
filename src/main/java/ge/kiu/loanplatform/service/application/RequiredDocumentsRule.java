package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.document.DocumentType;
import org.springframework.stereotype.Component;

import java.util.List;

// Rule 7 as an ApprovalRule: every document type the product requires must be uploaded before approval.
@Component
public class RequiredDocumentsRule implements ApprovalRule {

    @Override
    public String findProblem(LoanApplication application) {
        List<DocumentType> missing = application.findMissingDocumentTypes();
        if (missing.isEmpty()) {
            return null;
        }
        List<String> names = missing.stream().map(DocumentType::getName).toList();
        return "required documents are missing: " + String.join(", ", names);
    }
}
