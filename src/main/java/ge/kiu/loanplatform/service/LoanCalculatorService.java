package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.ScheduleEntryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The maths of a loan with equal monthly payments (an "annuity" loan).
 * It has no repository: it only calculates, so it needs no @Transactional.
 *
 * Every month the customer pays the same amount. One part of it is interest
 * (the monthly rate times what is still owed), the rest pays back the loan.
 * At the start most of the payment is interest; at the end most of it is the loan.
 */
@Service
public class LoanCalculatorService {

    // 16 significant digits for the steps in between; only the results are rounded to 2 decimals
    private static final MathContext PRECISION = MathContext.DECIMAL64;
    // yearly % -> monthly fraction: divide by 12 months and by 100 percent
    private static final BigDecimal MONTHS_TIMES_PERCENT = new BigDecimal("1200");

    // One line per month: payment, interest part, principal part, what is still owed afterwards
    public List<ScheduleEntryResponse> buildSchedule(BigDecimal amount, BigDecimal yearlyRatePercent, int termMonths) {
        BigDecimal monthlyRate = yearlyRatePercent.divide(MONTHS_TIMES_PERCENT, PRECISION);
        BigDecimal monthlyPayment = monthlyPayment(amount, monthlyRate, termMonths);

        List<ScheduleEntryResponse> schedule = new ArrayList<>();
        BigDecimal balance = amount;
        for (int month = 1; month <= termMonths; month++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal = monthlyPayment.subtract(interest);
            if (month == termMonths) {
                // last month: pay exactly what is left, so the cents lost by rounding do not stay as a debt
                principal = balance;
            }
            balance = balance.subtract(principal);
            schedule.add(new ScheduleEntryResponse(month, principal.add(interest), interest, principal, balance));
        }
        return schedule;
    }

    // The totals of a schedule
    public PaymentCalculationResponse summarize(BigDecimal amount, BigDecimal yearlyRatePercent, int termMonths,
                                                List<ScheduleEntryResponse> schedule) {
        BigDecimal totalPayment = BigDecimal.ZERO;
        for (ScheduleEntryResponse entry : schedule) {
            totalPayment = totalPayment.add(entry.payment());
        }
        BigDecimal monthlyPayment = schedule.getFirst().payment();
        return new PaymentCalculationResponse(amount, yearlyRatePercent, termMonths,
                monthlyPayment, totalPayment, totalPayment.subtract(amount));
    }

    // Annuity formula: payment = amount * r * (1 + r)^n / ((1 + r)^n - 1)
    // where r is the monthly rate and n the number of months
    private BigDecimal monthlyPayment(BigDecimal amount, BigDecimal monthlyRate, int termMonths) {
        if (monthlyRate.signum() == 0) {
            // 0% interest: the formula would divide by zero, and the answer is simply amount / months
            return amount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(termMonths, PRECISION); // (1 + r)^n
        return amount.multiply(monthlyRate).multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }
}
