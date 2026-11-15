package ge.kiu.loanplatform.exception;

// Thrown when something with the given id does not exist -> 404 Not Found
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
