package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Template method pattern: the steps every repayment schedule has, written once.
 *
 * Every month: interest on what is still owed, a part of the loan paid back, a new balance.
 * The last month pays exactly what is left, so the cents lost by rounding do not stay as a debt.
 * Only two steps are different for each repayment method, and those are the abstract methods
 * the subclasses fill in.
 *
 * It still implements RepaymentStrategy: the strategy pattern decides WHICH method is used,
 * this class decides HOW every method is built inside.
 * "abstract": it is not a complete strategy itself, so Spring never creates it (no @Component).
 */
public abstract class AbstractRepaymentStrategy implements RepaymentStrategy {

    // The template method. "final": a subclass cannot change the steps or their order,
    // it can only fill in regularAmount and principalPart.
    @Override
    public final List<ScheduleEntryResponse> buildSchedule(BigDecimal amount, BigDecimal monthlyRate, int termMonths) {
        // step 1 (different per method): the amount that stays the same every month
        BigDecimal regularAmount = regularAmount(amount, monthlyRate, termMonths);

        List<ScheduleEntryResponse> schedule = new ArrayList<>();
        BigDecimal balance = amount;
        for (int month = 1; month <= termMonths; month++) {
            // step 2 (the same for all): interest on what is still owed, rounded to cents
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            // step 3 (different per method): which part of the loan is paid back this month
            BigDecimal principal = principalPart(regularAmount, interest);
            if (month == termMonths) {
                // step 4 (the same for all): the last month pays exactly what is left
                principal = balance;
            }
            balance = balance.subtract(principal);
            schedule.add(new ScheduleEntryResponse(month, principal.add(interest), interest, principal, balance));
        }
        return schedule;
    }

    // Worked out once before the loop: the amount that stays the same every month
    // (annuity: the whole payment; differentiated: the part of the loan)
    protected abstract BigDecimal regularAmount(BigDecimal amount, BigDecimal monthlyRate, int termMonths);

    // The part of the loan paid back in one month, from the regular amount and this month's interest
    protected abstract BigDecimal principalPart(BigDecimal regularAmount, BigDecimal interest);
}
