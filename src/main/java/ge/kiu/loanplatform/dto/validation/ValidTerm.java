package ge.kiu.loanplatform.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// One reusable rule for every "term in months" value: it is required and must be 1..360.
// Before, @NotNull + @Min + @Max were copied onto every term field; now the limits live in one place
// (TermValidator) and a field only says @ValidTerm(label = "...").
// The label is the start of each error message, e.g. "Minimum term" -> "Minimum term must be at least 1 month".
@Constraint(validatedBy = TermValidator.class)              // the class that does the actual check
@Target({ElementType.FIELD, ElementType.PARAMETER})         // allowed on record fields and method parameters
@Retention(RetentionPolicy.RUNTIME)                         // kept at runtime so the validator can see it
public @interface ValidTerm {

    String label() default "Term";

    // The three attributes below are required by the Bean Validation specification for every constraint.
    // message is not used directly: TermValidator builds its own message from the label.
    String message() default "Invalid term";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
