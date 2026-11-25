package ge.kiu.loanplatform.dto.calculation;

import ge.kiu.loanplatform.entity.product.RepaymentType;

import java.math.BigDecimal;

// The result of the payment calculator: what a loan costs per month and in total
public record PaymentCalculationResponse(
        BigDecimal amount,
        BigDecimal interestRate,     // % per year, taken from the loan product
        Integer termMonths,
        RepaymentType repaymentType, // which strategy calculated the payments
        BigDecimal monthlyPayment,   // the first payment (annuity: every month the same)
        BigDecimal lastPayment,      // the last payment (annuity: a few cents apart; differentiated: the smallest)
        BigDecimal totalPayment,     // everything the customer pays back
        BigDecimal totalInterest     // totalPayment - amount
) {
}
