package ge.kiu.loanplatform.dto.error;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        ErrorCode code,                   // machine-readable kind of error, e.g. NOT_FOUND
        String message,
        String path,
        Map<String, String> fieldErrors,  // only filled for validation errors, otherwise null
        ApplicationStatus currentStatus,  // only filled for status errors, otherwise null
        List<ApplicationStatus> allowedStatuses // only for status errors: where a decision can still move it
) {
}
