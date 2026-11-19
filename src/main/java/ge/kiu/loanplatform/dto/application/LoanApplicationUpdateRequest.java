package ge.kiu.loanplatform.dto.application;

import ge.kiu.loanplatform.dto.validation.ValidAmount;
import ge.kiu.loanplatform.dto.validation.ValidTerm;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// What can be changed on an existing application. It has no customerId on purpose:
// an application always stays with the customer who submitted it.
public record LoanApplicationUpdateRequest(

        @NotNull(message = "Loan product id is required")
        @Positive(message = "Loan product id must be a positive number")
        Long loanProductId,

        @ValidAmount(label = "Amount")
        BigDecimal amount,

        @ValidTerm(label = "Term")
        Integer termMonths
) {
}
