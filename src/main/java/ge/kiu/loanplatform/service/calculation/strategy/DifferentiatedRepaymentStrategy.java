package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Strategy pattern: the "differentiated" loan.
 *
 * Every month the customer pays back the same part of the loan (amount / months),
 * plus the interest on what is still owed. What is owed gets smaller every month,
 * so the interest and the whole payment get smaller too: the first payment is the largest.
 * In total the customer pays less interest than with an annuity, because the loan shrinks faster.
 *
 * Template method pattern: the month-by-month loop is in AbstractRepaymentStrategy;
 * this class only fills in its two steps (amount / months, and always that same part).
 */
@Component
public class DifferentiatedRepaymentStrategy extends AbstractRepaymentStrategy {

    @Override
    public RepaymentType type() {
        return RepaymentType.DIFFERENTIATED;
    }

    // The regular amount is the part of the loan, e.g. 12000 over 12 months = 1000
    @Override
    protected BigDecimal regularAmount(BigDecimal amount, BigDecimal monthlyRate, int termMonths) {
        return amount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
    }

    // The part of the loan is the same every month; the interest only comes on top
    @Override
    protected BigDecimal principalPart(BigDecimal regularAmount, BigDecimal interest) {
        return regularAmount;
    }
}
