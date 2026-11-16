package ge.kiu.loanplatform.dto;

import ge.kiu.loanplatform.entity.ApplicationStatus;
import ge.kiu.loanplatform.entity.StatusChange;

import java.time.LocalDateTime;

// One line of an application's history
public record StatusChangeResponse(
        ApplicationStatus status,
        String decisionComment,   // the rejection reason, null for other statuses
        LocalDateTime changedAt
) {
    public static StatusChangeResponse from(StatusChange change) {
        return new StatusChangeResponse(change.getStatus(), change.getDecisionComment(), change.getChangedAt());
    }
}
