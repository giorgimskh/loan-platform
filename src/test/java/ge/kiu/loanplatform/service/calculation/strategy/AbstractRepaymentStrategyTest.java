package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of the template method itself, without the real repayment methods.
// A tiny subclass made only for this test fills in the two steps in the simplest way:
// "pay back 100 of the loan every month". So every check here is about the steps
// AbstractRepaymentStrategy does for ALL methods.
class AbstractRepaymentStrategyTest {

    // test-only subclass: remembers how the template called its steps
    private static class HundredEveryMonth extends AbstractRepaymentStrategy {
        int regularAmountCalls = 0;
        final List<BigDecimal> interestsSeen = new ArrayList<>();

        @Override
        public RepaymentType type() {
            return RepaymentType.DIFFERENTIATED; // not used by these tests
        }

        @Override
        protected BigDecimal regularAmount(BigDecimal amount, BigDecimal monthlyRate, int termMonths) {
            regularAmountCalls++;
            return new BigDecimal("100");
        }

        @Override
        protected BigDecimal principalPart(BigDecimal regularAmount, BigDecimal interest) {
            interestsSeen.add(interest);
            return regularAmount;
        }
    }

    private final HundredEveryMonth strategy = new HundredEveryMonth();
    private final BigDecimal onePercentPerMonth = new BigDecimal("0.01");

    @Test
    void buildSchedule_runsTheStepsOfTheSubclassAndAddsTheInterest() {
        // 250 at 1% per month for 3 months, paying back 100 every month
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("250"), onePercentPerMonth, 3);

        assertThat(schedule).hasSize(3); // one line per month
        assertThat(schedule.get(0).interest()).isEqualByComparingTo("2.50");   // 1% of 250
        assertThat(schedule.get(0).principal()).isEqualByComparingTo("100");   // from the subclass
        assertThat(schedule.get(0).payment()).isEqualByComparingTo("102.50");  // principal + interest
        assertThat(schedule.get(0).remainingBalance()).isEqualByComparingTo("150");
        assertThat(schedule.get(1).interest()).isEqualByComparingTo("1.50");   // 1% of 150
    }

    @Test
    void buildSchedule_letsTheLastMonthPayExactlyWhatIsLeft() {
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("250"), onePercentPerMonth, 3);

        // the subclass says 100, but only 50 is left: the template's own rule wins in the last month
        ScheduleEntryResponse last = schedule.getLast();
        assertThat(last.principal()).isEqualByComparingTo("50");
        assertThat(last.interest()).isEqualByComparingTo("0.50");
        assertThat(last.remainingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void buildSchedule_asksForTheRegularAmountOnce_andGivesEachMonthsInterestToTheSubclass() {
        strategy.buildSchedule(new BigDecimal("250"), onePercentPerMonth, 3);

        assertThat(strategy.regularAmountCalls).isEqualTo(1); // step 1 runs once, before the loop
        assertThat(strategy.interestsSeen).hasSize(3);         // step 3 runs every month
        assertThat(strategy.interestsSeen.get(0)).isEqualByComparingTo("2.50");
        assertThat(strategy.interestsSeen.get(2)).isEqualByComparingTo("0.50");
    }

    @Test
    void buildSchedule_isFinal_soNoSubclassCanChangeTheSteps() throws NoSuchMethodException {
        int modifiers = AbstractRepaymentStrategy.class
                .getMethod("buildSchedule", BigDecimal.class, BigDecimal.class, int.class)
                .getModifiers();

        assertThat(Modifier.isFinal(modifiers)).isTrue();
    }
}
