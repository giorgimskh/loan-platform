package ge.kiu.loanplatform.dto.application;

import ge.kiu.loanplatform.dto.file.ApplicationPart;
import ge.kiu.loanplatform.dto.file.ApplicationPartVisitor;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.StatusChange;

import java.time.LocalDateTime;

// One line of an application's history
public record StatusChangeResponse(
        ApplicationStatus status,
        String decisionComment,   // the rejection reason, null for other statuses
        LocalDateTime changedAt
) implements ApplicationPart {

    // Visitor pattern: send the visitor to the method for this type of part (double dispatch)
    @Override
    public void accept(ApplicationPartVisitor visitor) {
        visitor.visitHistoryLine(this);
    }

    public static StatusChangeResponse from(StatusChange change) {
        return new StatusChangeResponse(change.getStatus(), change.getDecisionComment(), change.getChangedAt());
    }
}
