package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.exception.custom.ConflictException;

import java.util.List;

// Command pattern: the "approve" decision.
// missingDocuments = the required document types not uploaded yet (empty list = nothing is missing).
// The service works the list out and passes it in, so the command needs no repository.
public record ApproveCommand(List<DocumentType> missingDocuments) implements DecisionCommand {

    @Override
    public ApplicationStatus targetStatus() {
        return ApplicationStatus.APPROVED;
    }

    @Override
    public void execute(LoanApplication application) {
        // Rule 7: every document the product requires must be uploaded before approval
        if (!missingDocuments.isEmpty()) {
            List<String> names = missingDocuments.stream().map(DocumentType::getName).toList();
            throw new ConflictException("Application with id " + application.getId()
                    + " cannot be approved, required documents are missing: " + String.join(", ", names));
        }
        application.setStatus(ApplicationStatus.APPROVED);
        application.setDecisionComment(null); // an approved application has no rejection reason
    }
}
