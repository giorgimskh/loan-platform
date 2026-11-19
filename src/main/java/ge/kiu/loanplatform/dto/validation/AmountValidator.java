package ge.kiu.loanplatform.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

// Checks a value annotated with @ValidAmount. Bean Validation creates this class itself
// (it is not a Spring bean, so it needs no stereotype annotation).
public class AmountValidator implements ConstraintValidator<ValidAmount, BigDecimal> {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    public static final int MAX_INTEGER_DIGITS = 13;   // digits before the decimal point
    public static final int MAX_FRACTION_DIGITS = 2;   // digits after the decimal point (cents)

    private String label;

    // Called once with the annotation, so we can remember its label
    @Override
    public void initialize(ValidAmount annotation) {
        this.label = annotation.label();
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        String error = null;
        if (value == null) {
            error = label + " is required";
        } else if (value.compareTo(MIN_AMOUNT) < 0) {   // compareTo, because equals() would also compare the scale
            error = label + " must be greater than 0";
        } else if (!hasAllowedDigits(value)) {
            error = label + " can have at most " + MAX_INTEGER_DIGITS + " digits before and "
                    + MAX_FRACTION_DIGITS + " after the decimal point";
        }

        if (error == null) {
            return true;
        }
        // Replace the default message ("Invalid amount") with the specific one we built above
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }

    // Same check as @Digits(integer = 13, fraction = 2).
    // stripTrailingZeros() makes 100.50 count as 100.5, so trailing zeros are not counted as decimals.
    // precision = number of all digits, scale = number of digits after the point.
    private boolean hasAllowedDigits(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        int integerDigits = stripped.precision() - stripped.scale();
        int fractionDigits = Math.max(stripped.scale(), 0);
        return integerDigits <= MAX_INTEGER_DIGITS && fractionDigits <= MAX_FRACTION_DIGITS;
    }
}
