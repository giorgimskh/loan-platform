package ge.kiu.loanplatform.service.application.command;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.exception.custom.ConflictException;

import java.util.List;

// Command pattern: the "approve" decision.
// problems = what the approval rules (ApprovalRule) object to (empty list = every rule is satisfied).
// The service asks the rules and passes the answers in, so the command needs no Spring beans.
public record ApproveCommand(List<String> problems) implements DecisionCommand {

    @Override
    public ApplicationStatus targetStatus() {
        return ApplicationStatus.APPROVED;
    }

    @Override
    public void execute(LoanApplication application) {
        // e.g. rule 7: every document the product requires must be uploaded before approval
        if (!problems.isEmpty()) {
            throw new ConflictException("Application with id " + application.getId()
                    + " cannot be approved, " + String.join("; ", problems));
        }
        application.setStatus(ApplicationStatus.APPROVED);
        application.setDecisionComment(null); // an approved application has no rejection reason
    }
}
