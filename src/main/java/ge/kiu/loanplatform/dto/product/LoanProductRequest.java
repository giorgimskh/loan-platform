package ge.kiu.loanplatform.dto.product;

import ge.kiu.loanplatform.dto.validation.ValidAmount;
import ge.kiu.loanplatform.dto.validation.ValidTerm;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

        @ValidAmount(label = "Minimum amount")
        BigDecimal minAmount,

        @ValidAmount(label = "Maximum amount")
        BigDecimal maxAmount,

        @ValidTerm(label = "Minimum term")
        Integer minTermMonths,

        @ValidTerm(label = "Maximum term")
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
