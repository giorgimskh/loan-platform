package ge.kiu.loanplatform.exception.custom;

// Thrown when the request is well-formed but breaks a business rule
// (amount/term outside the product range, minAmount > maxAmount, wrong file type or size) -> 400 Bad Request
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
