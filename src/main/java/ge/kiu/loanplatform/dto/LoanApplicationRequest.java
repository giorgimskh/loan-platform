package ge.kiu.loanplatform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LoanApplicationRequest(

        @NotNull(message = "Customer id is required")
        Long customerId,

        @NotNull(message = "Loan product id is required")
        Long loanProductId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Term is required")
        @Min(value = 1, message = "Term must be at least 1 month")
        @Max(value = 360, message = "Term must be at most 360 months")
        Integer termMonths
) {
}
