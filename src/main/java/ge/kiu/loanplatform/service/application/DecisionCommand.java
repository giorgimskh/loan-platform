package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;

// Command pattern: one officer decision (approve or reject) as an object.
// The object carries everything it needs (for example the rejection reason), and
// LoanApplicationService runs the steps every decision shares around execute().
public interface DecisionCommand {

    // the status the application gets; also used for rule 4 (a decision cannot be repeated)
    ApplicationStatus targetStatus();

    // the part that is different for each decision: its own check and the changes to the application
    void execute(LoanApplication application);
}
