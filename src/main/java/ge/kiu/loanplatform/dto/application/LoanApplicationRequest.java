package ge.kiu.loanplatform.dto.application;

import ge.kiu.loanplatform.dto.validation.ValidAmount;
import ge.kiu.loanplatform.dto.validation.ValidTerm;
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

        @ValidAmount(label = "Amount")
        BigDecimal amount,

        @ValidTerm(label = "Term")
        Integer termMonths
) {
}
