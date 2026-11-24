package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;

// Observer pattern: the message LoanApplicationService sends out when an application gets a new status.
// It carries plain values, not the entity, so listeners need no database access to read it.
// previousStatus is null for a new application (it had no status before).
public record ApplicationStatusChangedEvent(
        Long applicationId,
        String customerFullName,
        String customerEmail,
        ApplicationStatus previousStatus,
        ApplicationStatus newStatus,
        String decisionComment
) {
}
