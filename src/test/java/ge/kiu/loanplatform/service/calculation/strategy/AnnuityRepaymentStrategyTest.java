package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of the annuity strategy on its own: no Spring, no mocks.
// The detailed annuity maths is also tested through LoanCalculatorServiceTest.
class AnnuityRepaymentStrategyTest {

    private final AnnuityRepaymentStrategy strategy = new AnnuityRepaymentStrategy();

    @Test
    void type_isAnnuity() {
        assertThat(strategy.type()).isEqualTo(RepaymentType.ANNUITY);
    }

    @Test
    void buildSchedule_paysTheSameAmountEveryMonth() {
        // 10000 at 1% per month for 12 months: the well-known answer is 888.49 per month
        List<ScheduleEntryResponse> schedule = strategy.buildSchedule(new BigDecimal("10000"), new BigDecimal("0.01"), 12);

        for (int month = 0; month < 11; month++) {
            assertThat(schedule.get(month).payment()).isEqualByComparingTo("888.49");
        }
        // only the last payment may differ by a few cents: it closes the loan exactly
        assertThat(schedule.getLast().remainingBalance()).isEqualByComparingTo("0");
    }
}
