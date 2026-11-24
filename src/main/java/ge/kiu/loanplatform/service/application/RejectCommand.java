package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;

// Command pattern: the "reject" decision. The reason travels inside the command object.
// Rule 5 (non-blank reason) is already checked by @NotBlank on RejectRequest.
public record RejectCommand(String reason) implements DecisionCommand {

    @Override
    public ApplicationStatus targetStatus() {
        return ApplicationStatus.REJECTED;
    }

    @Override
    public void execute(LoanApplication application) {
        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecisionComment(reason);
    }
}
