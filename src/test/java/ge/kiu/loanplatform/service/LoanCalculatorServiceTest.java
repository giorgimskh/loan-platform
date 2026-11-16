package ge.kiu.loanplatform.service;

import ge.kiu.loanplatform.dto.PaymentCalculationResponse;
import ge.kiu.loanplatform.dto.ScheduleEntryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of the loan maths. The class has no dependencies, so no mocks are needed.
class LoanCalculatorServiceTest {

    private final LoanCalculatorService calculator = new LoanCalculatorService();

    @Test
    void buildSchedule_usesTheAnnuityFormula() {
        // 10000 at 12% per year (1% per month) for 12 months: the well-known answer is 888.49 per month
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(new BigDecimal("10000"), new BigDecimal("12"), 12);

        assertThat(schedule).hasSize(12);
        ScheduleEntryResponse first = schedule.getFirst();
        assertThat(first.payment()).isEqualByComparingTo("888.49");
        assertThat(first.interest()).isEqualByComparingTo("100.00");   // 1% of 10000
        assertThat(first.principal()).isEqualByComparingTo("788.49");  // payment - interest
        assertThat(first.remainingBalance()).isEqualByComparingTo("9211.51");
    }

    @Test
    void buildSchedule_endsWithNothingLeftToPay() {
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(new BigDecimal("5000"), new BigDecimal("14.50"), 12);

        assertThat(schedule.getLast().remainingBalance()).isEqualByComparingTo("0");
        // all principal parts together are exactly the loan
        BigDecimal paidBack = BigDecimal.ZERO;
        for (ScheduleEntryResponse entry : schedule) {
            paidBack = paidBack.add(entry.principal());
        }
        assertThat(paidBack).isEqualByComparingTo("5000");
    }

    @Test
    void buildSchedule_paysLessInterestEveryMonth() {
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(new BigDecimal("5000"), new BigDecimal("14.50"), 12);

        assertThat(schedule.get(1).interest()).isLessThan(schedule.get(0).interest());
        assertThat(schedule.getLast().interest()).isLessThan(schedule.get(1).interest());
    }

    @Test
    void buildSchedule_dividesTheAmountEvenly_whenInterestIsZero() {
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(new BigDecimal("1200"), BigDecimal.ZERO, 12);

        assertThat(schedule.getFirst().payment()).isEqualByComparingTo("100.00");
        assertThat(schedule.getFirst().interest()).isEqualByComparingTo("0");
        assertThat(schedule.getLast().remainingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void summarize_addsUpThePayments() {
        BigDecimal amount = new BigDecimal("5000");
        BigDecimal rate = new BigDecimal("14.50");
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(amount, rate, 12);

        PaymentCalculationResponse summary = calculator.summarize(amount, rate, 12, schedule);

        assertThat(summary.monthlyPayment()).isEqualByComparingTo("450.11");
        assertThat(summary.totalPayment()).isEqualByComparingTo("5401.36");
        assertThat(summary.totalInterest()).isEqualByComparingTo("401.36"); // total - amount
    }
}
