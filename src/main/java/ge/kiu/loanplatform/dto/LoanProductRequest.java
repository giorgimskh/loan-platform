package ge.kiu.loanplatform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record LoanProductRequest(

        // Whitelist: letters, digits, spaces and a few punctuation marks. Blocks < > " ; etc.
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N} .,&()'-]*$",
                message = "Name may contain only letters, digits, spaces and . , & ( ) ' -")
        String name,

        // @Digits matches the database column (precision 5, scale 2): max 3 digits before the point, 2 after
        @NotNull(message = "Interest rate is required")
        @DecimalMin(value = "0.0", message = "Interest rate cannot be negative")
        @DecimalMax(value = "100.0", message = "Interest rate cannot be more than 100")
        @Digits(integer = 3, fraction = 2, message = "Interest rate can have at most 2 decimal places")
        BigDecimal interestRate,

        // @Digits matches the database column (precision 15, scale 2): stops huge numbers
        // and silent rounding of values like 100.999
        @NotNull(message = "Minimum amount is required")
        @DecimalMin(value = "0.01", message = "Minimum amount must be greater than 0")
        @Digits(integer = 13, fraction = 2,
                message = "Minimum amount can have at most 13 digits before and 2 after the decimal point")
        BigDecimal minAmount,

        @NotNull(message = "Maximum amount is required")
        @DecimalMin(value = "0.01", message = "Maximum amount must be greater than 0")
        @Digits(integer = 13, fraction = 2,
                message = "Maximum amount can have at most 13 digits before and 2 after the decimal point")
        BigDecimal maxAmount,

        @NotNull(message = "Minimum term is required")
        @Min(value = 1, message = "Minimum term must be at least 1 month")
        @Max(value = 360, message = "Minimum term must be at most 360 months")
        Integer minTermMonths,

        @NotNull(message = "Maximum term is required")
        @Min(value = 1, message = "Maximum term must be at least 1 month")
        @Max(value = 360, message = "Maximum term must be at most 360 months")
        Integer maxTermMonths,

        // ids of DocumentTypes this product requires; may be empty.
        // @Size limits how many ids one request can send (protects the database from a huge list).
        // The annotations inside <...> are checked for every element of the set.
        @NotNull(message = "Required document type ids are required (use [] for none)")
        @Size(max = 20, message = "A product can require at most 20 document types")
        Set<@NotNull(message = "Document type id cannot be null")
            @Positive(message = "Document type id must be a positive number") Long> requiredDocumentTypeIds
) {
}
