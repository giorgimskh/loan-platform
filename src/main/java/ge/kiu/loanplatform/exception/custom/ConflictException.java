package ge.kiu.loanplatform.exception.custom;

// Thrown when the request conflicts with the current state of the data
// (duplicate email/personal number/product name, or the application is no longer SUBMITTED) -> 409 Conflict
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
