package ge.kiu.loanplatform.service.application.rule;

import ge.kiu.loanplatform.entity.application.LoanApplication;

// Chain of responsibility: one check an application must pass before it can be approved.
// LoanApplicationService asks every rule in turn; each rule only knows its own check.
// Today there is one rule (RequiredDocumentsRule, rule 7). A new check is a new class that
// implements this interface and is marked @Component; the service picks it up without changes.
public interface ApprovalRule {

    // null when the application passes this check,
    // otherwise why it cannot be approved, e.g. "required documents are missing: ID Card"
    String findProblem(LoanApplication application);
}
