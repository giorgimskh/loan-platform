package ge.kiu.loanplatform.service.calculation;

import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import ge.kiu.loanplatform.service.calculation.strategy.RepaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * The loan calculator. How the monthly payments are worked out is a strategy (RepaymentStrategy);
 * this class turns the yearly rate into a monthly one, asks the strategy for the schedule,
 * and adds up the totals.
 * It has no repository: it only calculates, so it needs no @Transactional.
 */
@Service
@RequiredArgsConstructor
public class LoanCalculatorService {

    // 16 significant digits for the steps in between; only the results are rounded to 2 decimals
    private static final MathContext PRECISION = MathContext.DECIMAL64;
    // yearly % -> monthly fraction: divide by 12 months and by 100 percent
    private static final BigDecimal MONTHS_TIMES_PERCENT = new BigDecimal("1200");

    // Strategy pattern: the calculator only knows the interface.
    // Spring puts every bean that implements RepaymentStrategy into this list
    // (AnnuityRepaymentStrategy, DifferentiatedRepaymentStrategy), so a new repayment method
    // is just a new @Component class; this service does not change.
    private final List<RepaymentStrategy> repaymentStrategies;

    // One line per month: payment, interest part, principal part, what is still owed afterwards
    public List<ScheduleEntryResponse> buildSchedule(RepaymentType type, BigDecimal amount,
                                                     BigDecimal yearlyRatePercent, int termMonths) {
        BigDecimal monthlyRate = yearlyRatePercent.divide(MONTHS_TIMES_PERCENT, PRECISION);
        return strategyFor(type).buildSchedule(amount, monthlyRate, termMonths);
    }

    // The totals of a schedule
    public PaymentCalculationResponse summarize(RepaymentType type, BigDecimal amount, BigDecimal yearlyRatePercent,
                                                int termMonths, List<ScheduleEntryResponse> schedule) {
        BigDecimal totalPayment = BigDecimal.ZERO;
        for (ScheduleEntryResponse entry : schedule) {
            totalPayment = totalPayment.add(entry.payment());
        }
        // first and last payment: the same for an annuity (except rounding cents),
        // the largest and the smallest for a differentiated loan
        BigDecimal monthlyPayment = schedule.getFirst().payment();
        BigDecimal lastPayment = schedule.getLast().payment();
        return new PaymentCalculationResponse(amount, yearlyRatePercent, termMonths, type,
                monthlyPayment, lastPayment, totalPayment, totalPayment.subtract(amount));
    }

    // Picks the strategy that calculates the given repayment type
    private RepaymentStrategy strategyFor(RepaymentType type) {
        for (RepaymentStrategy strategy : repaymentStrategies) {
            if (strategy.type() == type) {
                return strategy;
            }
        }
        // only possible if someone adds a RepaymentType but forgets its strategy class: a programming error
        throw new IllegalStateException("No repayment strategy for " + type);
    }
}
