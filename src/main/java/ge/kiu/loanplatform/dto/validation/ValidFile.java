package ge.kiu.loanplatform.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Rule 6 for an uploaded file, as one reusable annotation: the file is present (not missing, not empty),
// at most 5MB, and declared as PDF, JPG or PNG (file extension AND Content-Type must both say so).
// An image must also really be that format and have at most 25 million pixels (no "pixel bombs");
// only its header is read for that, the pixels are never decoded.
// Used on the "file" parameter of the upload endpoint; a failure becomes a 400 "Validation failed"
// response with the reason under fieldErrors.file (GlobalExceptionHandler.handleMethodValidation).
@Constraint(validatedBy = FileValidator.class)              // the class that does the actual check
@Target({ElementType.FIELD, ElementType.PARAMETER})         // allowed on fields and method parameters
@Retention(RetentionPolicy.RUNTIME)                         // kept at runtime so the validator can see it
public @interface ValidFile {

    // Required by the Bean Validation specification for every constraint.
    // message is not used directly: FileValidator builds its own message for each problem.
    String message() default "Invalid file";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
