package ge.kiu.loanplatform.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// Checks a value annotated with @ValidTerm. Bean Validation creates this class itself
// (it is not a Spring bean, so it needs no stereotype annotation).
public class TermValidator implements ConstraintValidator<ValidTerm, Integer> {

    public static final int MIN_TERM_MONTHS = 1;
    public static final int MAX_TERM_MONTHS = 360;

    private String label;

    // Called once with the annotation, so we can remember its label
    @Override
    public void initialize(ValidTerm annotation) {
        this.label = annotation.label();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        String error = null;
        if (value == null) {
            error = label + " is required";
        } else if (value < MIN_TERM_MONTHS) {
            error = label + " must be at least " + MIN_TERM_MONTHS + " month";
        } else if (value > MAX_TERM_MONTHS) {
            error = label + " must be at most " + MAX_TERM_MONTHS + " months";
        }

        if (error == null) {
            return true;
        }
        // Replace the default message ("Invalid term") with the specific one we built above
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }
}
