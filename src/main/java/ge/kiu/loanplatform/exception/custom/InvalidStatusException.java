package ge.kiu.loanplatform.exception.custom;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import lombok.Getter;

import java.util.List;

// Thrown when an action is not allowed in the application's current status -> 409 Conflict.
// It extends ConflictException, so every place that already handles a conflict
// (GlobalExceptionHandler, the website's WebControllerAdvice) handles this one too.
// Extra compared to a plain conflict: an exact error code and the status details,
// which GlobalExceptionHandler puts into the JSON (currentStatus, allowedStatuses).
@Getter
public class InvalidStatusException extends ConflictException {

    private final ErrorCode code;
    private final ApplicationStatus currentStatus;

    // message = what went wrong; the status itself adds what is still allowed (State pattern)
    public InvalidStatusException(ErrorCode code, ApplicationStatus currentStatus, String message) {
        super(message + " " + currentStatus.describeAllowedActions());
        this.code = code;
        this.currentStatus = currentStatus;
    }

    // the statuses an officer decision can still move the application to
    public List<ApplicationStatus> getAllowedStatuses() {
        return currentStatus.allowedNextStatuses();
    }
}
