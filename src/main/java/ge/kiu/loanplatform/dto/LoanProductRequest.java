package ge.kiu.loanplatform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record LoanProductRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Interest rate is required")
        @DecimalMin(value = "0.0", message = "Interest rate cannot be negative")
        @DecimalMax(value = "100.0", message = "Interest rate cannot be more than 100")
        BigDecimal interestRate,

        @NotNull(message = "Minimum amount is required")
        @DecimalMin(value = "0.01", message = "Minimum amount must be greater than 0")
        BigDecimal minAmount,

        @NotNull(message = "Maximum amount is required")
        @DecimalMin(value = "0.01", message = "Maximum amount must be greater than 0")
        BigDecimal maxAmount,

        @NotNull(message = "Minimum term is required")
        @Min(value = 1, message = "Minimum term must be at least 1 month")
        @Max(value = 360, message = "Minimum term must be at most 360 months")
        Integer minTermMonths,

        @NotNull(message = "Maximum term is required")
        @Min(value = 1, message = "Maximum term must be at least 1 month")
        @Max(value = 360, message = "Maximum term must be at most 360 months")
        Integer maxTermMonths,

        // ids of DocumentTypes this product requires; may be empty
        @NotNull(message = "Required document type ids are required (use [] for none)")
        Set<Long> requiredDocumentTypeIds
) {
}
