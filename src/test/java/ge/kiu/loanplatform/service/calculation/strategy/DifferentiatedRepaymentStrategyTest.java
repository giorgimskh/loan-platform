package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of one strategy on its own: no Spring, no mocks.
// The strategy gets the MONTHLY rate as a fraction (0.01 = 1% per month).
class DifferentiatedRepaymentStrategyTest {

    private final DifferentiatedRepaymentStrategy strategy = new DifferentiatedRepaymentStrategy();
    private final BigDecimal onePercentPerMonth = new BigDecimal("0.01");

    @Test
    void type_isDifferentiated() {
        assertThat(strategy.type()).isEqualTo(RepaymentType.DIFFERENTIATED);
    }

    @Test
    void buildSchedule_paysTheSamePartOfTheLoanEveryMonth_plusInterestOnWhatIsLeft() {
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("12000"), onePercentPerMonth, 12);

        assertThat(schedule).hasSize(12);
        ScheduleEntryResponse first = schedule.getFirst();
        assertThat(first.principal()).isEqualByComparingTo("1000.00");      // 12000 / 12
        assertThat(first.interest()).isEqualByComparingTo("120.00");        // 1% of 12000
        assertThat(first.payment()).isEqualByComparingTo("1120.00");
        assertThat(first.remainingBalance()).isEqualByComparingTo("11000.00");

        ScheduleEntryResponse second = schedule.get(1);
        assertThat(second.principal()).isEqualByComparingTo("1000.00");     // the same part of the loan
        assertThat(second.interest()).isEqualByComparingTo("110.00");       // 1% of 11000
    }

    @Test
    void buildSchedule_paysLessEveryMonth() {
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("12000"), onePercentPerMonth, 12);

        for (int i = 1; i < schedule.size(); i++) {
            assertThat(schedule.get(i).payment()).isLessThan(schedule.get(i - 1).payment());
        }
    }

    @Test
    void buildSchedule_endsWithNothingLeftToPay_evenWhenTheAmountDoesNotDivideEvenly() {
        // 1000 / 3 = 333.33, so the last month must pay 333.34 to close the loan
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("1000"), onePercentPerMonth, 3);

        assertThat(schedule.get(0).principal()).isEqualByComparingTo("333.33");
        assertThat(schedule.get(2).principal()).isEqualByComparingTo("333.34");
        assertThat(schedule.getLast().remainingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void buildSchedule_dividesTheAmountEvenly_whenInterestIsZero() {
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("1200"), BigDecimal.ZERO, 12);

        assertThat(schedule.getFirst().payment()).isEqualByComparingTo("100.00");
        assertThat(schedule.getLast().payment()).isEqualByComparingTo("100.00");
        assertThat(schedule.getLast().remainingBalance()).isEqualByComparingTo("0");
    }
}
