package ge.kiu.loanplatform.dto;

import java.math.BigDecimal;

// The result of the payment calculator: what a loan costs per month and in total
public record PaymentCalculationResponse(
        BigDecimal amount,
        BigDecimal interestRate,     // % per year, taken from the loan product
        Integer termMonths,
        BigDecimal monthlyPayment,
        BigDecimal totalPayment,     // everything the customer pays back
        BigDecimal totalInterest     // totalPayment - amount
) {
}
