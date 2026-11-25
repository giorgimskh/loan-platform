package ge.kiu.loanplatform.exception.custom;

// A fixed, machine-readable name for every kind of error the API returns (the "code" field in the JSON).
// The message is for people and may be reworded; the code is for programs (a frontend, Postman tests)
// and never changes, so they can check "code == NOT_FOUND" instead of comparing message text.
public enum ErrorCode {

    // our custom exceptions
    NOT_FOUND,                  // ResourceNotFoundException -> 404
    BUSINESS_RULE_VIOLATION,    // BusinessRuleException -> 400
    CONFLICT,                   // ConflictException -> 409

    // InvalidStatusException -> 409: the action is not allowed in the application's status
    APPLICATION_NOT_MODIFIABLE, // modify, cancel or delete a document after a decision
    INVALID_STATUS_TRANSITION,  // rule 4: repeat a decision (approve an APPROVED application)
    NO_DECISION_TO_UNDO,        // undo on a SUBMITTED application
    NO_REPAYMENT_SCHEDULE,      // schedule of an application that is not APPROVED

    // exceptions thrown by Spring itself
    VALIDATION_FAILED,          // @Valid or a parameter constraint failed -> 400
    MALFORMED_REQUEST_BODY,     // body missing, not JSON, or a value of the wrong type -> 400
    INVALID_PARAMETER,          // path variable or request param of the wrong type -> 400
    MISSING_PARAMETER,          // a required request param or multipart part is missing -> 400
    INVALID_SORT_FIELD,         // ?sort= names a field that does not exist -> 400
    FILE_TOO_LARGE,             // upload above the multipart size limit -> 400
    METHOD_NOT_ALLOWED,         // URL exists, but not for this HTTP method -> 405
    DATA_INTEGRITY_VIOLATION    // the database refused the change -> 409
}
