package ge.kiu.loanplatform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LoanApplicationRequest(

        @NotNull(message = "Customer id is required")
        @Positive(message = "Customer id must be a positive number")
        Long customerId,

        @NotNull(message = "Loan product id is required")
        @Positive(message = "Loan product id must be a positive number")
        Long loanProductId,

        // @Digits matches the database column (precision 15, scale 2): stops huge numbers
        // and silent rounding of values like 100.999
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 13, fraction = 2,
                message = "Amount can have at most 13 digits before and 2 after the decimal point")
        BigDecimal amount,

        @NotNull(message = "Term is required")
        @Min(value = 1, message = "Term must be at least 1 month")
        @Max(value = 360, message = "Term must be at most 360 months")
        Integer termMonths
) {
}
