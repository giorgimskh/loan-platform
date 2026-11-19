package ge.kiu.loanplatform.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// One reusable rule for every money amount: it is required, greater than 0 and fits the database column
// (precision 15, scale 2 = at most 13 digits before and 2 after the decimal point).
// Before, @NotNull + @DecimalMin + @Digits were copied onto every amount field; now the limits live in one
// place (AmountValidator) and a field only says @ValidAmount(label = "...").
// The label is the start of each error message, e.g. "Minimum amount" -> "Minimum amount must be greater than 0".
@Constraint(validatedBy = AmountValidator.class)            // the class that does the actual check
@Target({ElementType.FIELD, ElementType.PARAMETER})         // allowed on record fields and method parameters
@Retention(RetentionPolicy.RUNTIME)                         // kept at runtime so the validator can see it
public @interface ValidAmount {

    String label() default "Amount";

    // The three attributes below are required by the Bean Validation specification for every constraint.
    // message is not used directly: AmountValidator builds its own message from the label.
    String message() default "Invalid amount";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
