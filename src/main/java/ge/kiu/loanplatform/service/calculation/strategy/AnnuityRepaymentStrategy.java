package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Strategy pattern: the "annuity" loan, where the customer pays the same amount every month.
 *
 * One part of each payment is interest (the monthly rate times what is still owed), the rest pays back the loan.
 * At the start most of the payment is interest; at the end most of it is the loan.
 *
 * Template method pattern: the month-by-month loop is in AbstractRepaymentStrategy;
 * this class only fills in its two steps (the fixed payment, and payment - interest).
 *
 * {@code @Component}: Spring creates one instance and puts it into LoanCalculatorService's list of strategies.
 * It has no fields that change, so one shared instance is safe for every request.
 */
@Component
public class AnnuityRepaymentStrategy extends AbstractRepaymentStrategy {

    // 16 significant digits for the steps in between; only the results are rounded to 2 decimals
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    @Override
    public RepaymentType type() {
        return RepaymentType.ANNUITY;
    }

    // The regular amount is the whole monthly payment.
    // Annuity formula: payment = amount * r * (1 + r)^n / ((1 + r)^n - 1)
    // where r is the monthly rate and n the number of months
    @Override
    protected BigDecimal regularAmount(BigDecimal amount, BigDecimal monthlyRate, int termMonths) {
        if (monthlyRate.signum() == 0) {
            // 0% interest: the formula would divide by zero, and the answer is simply amount / months
            return amount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(termMonths, PRECISION); // (1 + r)^n
        return amount.multiply(monthlyRate).multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    // The payment stays the same, so whatever the interest does not take pays back the loan
    @Override
    protected BigDecimal principalPart(BigDecimal regularAmount, BigDecimal interest) {
        return regularAmount.subtract(interest);
    }
}
