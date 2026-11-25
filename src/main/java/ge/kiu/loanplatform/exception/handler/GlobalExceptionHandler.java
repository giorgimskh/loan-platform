package ge.kiu.loanplatform.exception.handler;

import ge.kiu.loanplatform.dto.error.ErrorResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ErrorCode;
import ge.kiu.loanplatform.exception.custom.InvalidStatusException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- our custom exceptions ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), request, null);
    }

    // A ConflictException about the application's status. Spring picks the most specific handler,
    // so this one wins over handleConflict for an InvalidStatusException.
    @ExceptionHandler(InvalidStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatus(InvalidStatusException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, null,
                ex.getCurrentStatus(), ex.getAllowedStatuses());
    }

    // ---------- exceptions thrown by Spring itself ----------

    // @Valid failed on a @RequestBody DTO -> list every invalid field
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request, fieldErrors);
    }

    // A constraint written directly on a controller parameter failed
    // (e.g. @Positive on the id in /api/customers/-5).
    // When a method has such a parameter, Spring also reports its @Valid body errors here,
    // so we handle both kinds and keep the same JSON shape as handleValidation.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                // the @Valid @RequestBody DTO: one entry per invalid field
                for (FieldError error : bodyErrors.getFieldErrors()) {
                    fieldErrors.put(error.getField(), error.getDefaultMessage());
                }
            } else {
                // a simple parameter like id: use the parameter name as the key
                String parameterName = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    fieldErrors.put(parameterName, error.getDefaultMessage());
                }
            }
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request, fieldErrors);
    }

    // Body is not valid JSON, or a value has the wrong type (e.g. "amount": "abc")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST_BODY, "Request body is missing or malformed", request, null);
    }

    // Path variable or request param has the wrong type (e.g. /api/customers/abc or ?status=WRONG)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER, message, request, null);
    }

    // A required @RequestParam is missing (e.g. upload without documentTypeId)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_PARAMETER, "Missing required parameter '" + ex.getParameterName() + "'", request, null);
    }

    // The URL exists but not for this HTTP method (e.g. GET /api/customers, which only accepts POST)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported for this URL", request, null);
    }

    // The multipart request has no "file" part
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_PARAMETER, "Missing required part '" + ex.getRequestPartName() + "'", request, null);
    }

    // Sorting by a field that does not exist (e.g. ?sort=wrongField)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSort(PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SORT_FIELD, "Cannot sort by unknown field '" + ex.getPropertyName() + "'", request, null);
    }

    // Uploaded file is bigger than spring.servlet.multipart.max-file-size
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.FILE_TOO_LARGE, "File is too large. Maximum size is 5MB", request, null);
    }

    // Database refused the change (unique value already taken, or row still referenced by another table)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCode.DATA_INTEGRITY_VIOLATION, "Operation conflicts with existing data", request, null);
    }

    // ---------- helper ----------

    // every error except a status error: no status details
    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
                                                HttpServletRequest request, Map<String, String> fieldErrors) {
        return build(status, code, message, request, fieldErrors, null, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
                                                HttpServletRequest request, Map<String, String> fieldErrors,
                                                ApplicationStatus currentStatus, List<ApplicationStatus> allowedStatuses) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors,
                currentStatus,
                allowedStatuses
        );
        return ResponseEntity.status(status).body(body);
    }
}
