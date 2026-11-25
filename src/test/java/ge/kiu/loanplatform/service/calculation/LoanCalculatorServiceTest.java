package ge.kiu.loanplatform.service.calculation;

import ge.kiu.loanplatform.dto.calculation.PaymentCalculationResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import ge.kiu.loanplatform.service.calculation.strategy.AnnuityRepaymentStrategy;
import ge.kiu.loanplatform.service.calculation.strategy.DifferentiatedRepaymentStrategy;
import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of the calculator: rate conversion, choosing the strategy, totals.
// The strategies are plain objects, so they are passed in by hand: no Spring, no mocks.
// The annuity tests go through the calculator on purpose: the same numbers as before the strategies existed.
class LoanCalculatorServiceTest {

    private final LoanCalculatorService calculator = new LoanCalculatorService(
            List.of(new AnnuityRepaymentStrategy(), new DifferentiatedRepaymentStrategy()));

    @Test
    void buildSchedule_usesTheAnnuityFormula() {
        // 10000 at 12% per year (1% per month) for 12 months: the well-known answer is 888.49 per month
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(RepaymentType.ANNUITY, new BigDecimal("10000"), new BigDecimal("12"), 12);

        assertThat(schedule).hasSize(12);
        ScheduleEntryResponse first = schedule.getFirst();
        assertThat(first.payment()).isEqualByComparingTo("888.49");
        assertThat(first.interest()).isEqualByComparingTo("100.00");   // 1% of 10000
        assertThat(first.principal()).isEqualByComparingTo("788.49");  // payment - interest
        assertThat(first.remainingBalance()).isEqualByComparingTo("9211.51");
    }

    @Test
    void buildSchedule_endsWithNothingLeftToPay() {
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(RepaymentType.ANNUITY, new BigDecimal("5000"), new BigDecimal("14.50"), 12);

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
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(RepaymentType.ANNUITY, new BigDecimal("5000"), new BigDecimal("14.50"), 12);

        assertThat(schedule.get(1).interest()).isLessThan(schedule.get(0).interest());
        assertThat(schedule.getLast().interest()).isLessThan(schedule.get(1).interest());
    }

    @Test
    void buildSchedule_dividesTheAmountEvenly_whenInterestIsZero() {
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(RepaymentType.ANNUITY, new BigDecimal("1200"), BigDecimal.ZERO, 12);

        assertThat(schedule.getFirst().payment()).isEqualByComparingTo("100.00");
        assertThat(schedule.getFirst().interest()).isEqualByComparingTo("0");
        assertThat(schedule.getLast().remainingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void summarize_addsUpThePayments() {
        BigDecimal amount = new BigDecimal("5000");
        BigDecimal rate = new BigDecimal("14.50");
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(RepaymentType.ANNUITY, amount, rate, 12);

        PaymentCalculationResponse summary = calculator.summarize(RepaymentType.ANNUITY, amount, rate, 12, schedule);

        assertThat(summary.monthlyPayment()).isEqualByComparingTo("450.11");
        assertThat(summary.lastPayment()).isEqualByComparingTo("450.15"); // the last month closes the loan exactly
        assertThat(summary.totalPayment()).isEqualByComparingTo("5401.36");
        assertThat(summary.totalInterest()).isEqualByComparingTo("401.36"); // total - amount
    }

    @Test
    void buildSchedule_usesTheDifferentiatedStrategy_whenThatTypeIsAsked() {
        // 12000 at 12% per year (1% per month) for 12 months: 1000 of the loan every month plus interest
        List<ScheduleEntryResponse> schedule =
                calculator.buildSchedule(RepaymentType.DIFFERENTIATED, new BigDecimal("12000"), new BigDecimal("12"), 12);

        assertThat(schedule.getFirst().payment()).isEqualByComparingTo("1120.00"); // 1000 + 1% of 12000
        assertThat(schedule.getLast().payment()).isEqualByComparingTo("1010.00");  // 1000 + 1% of 1000
    }

    @Test
    void summarize_addsUpTheDifferentiatedPayments() {
        BigDecimal amount = new BigDecimal("12000");
        BigDecimal rate = new BigDecimal("12");
        List<ScheduleEntryResponse> schedule = calculator.buildSchedule(RepaymentType.DIFFERENTIATED, amount, rate, 12);

        PaymentCalculationResponse summary = calculator.summarize(RepaymentType.DIFFERENTIATED, amount, rate, 12, schedule);

        // interest: 120 + 110 + ... + 10 = 780, less than the annuity would cost (794.23)
        assertThat(summary.repaymentType()).isEqualTo(RepaymentType.DIFFERENTIATED);
        assertThat(summary.monthlyPayment()).isEqualByComparingTo("1120.00"); // the first, largest payment
        assertThat(summary.lastPayment()).isEqualByComparingTo("1010.00");    // the last, smallest payment
        assertThat(summary.totalInterest()).isEqualByComparingTo("780.00");
        assertThat(summary.totalPayment()).isEqualByComparingTo("12780.00");
    }
}
