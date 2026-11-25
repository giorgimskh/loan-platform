package ge.kiu.loanplatform.service.calculation.strategy;

import ge.kiu.loanplatform.dto.calculation.ScheduleEntryResponse;
import ge.kiu.loanplatform.entity.product.RepaymentType;

import java.math.BigDecimal;
import java.util.List;

// Strategy pattern: one way of paying a loan back.
// Every repayment method answers the same question ("what does the customer pay each month?")
// with its own algorithm. LoanCalculatorService only knows this interface, so a new method
// is a new class and the service does not change.
public interface RepaymentStrategy {

    // the repayment type this strategy calculates; LoanCalculatorService picks the strategy by it
    RepaymentType type();

    // One line per month: payment, interest part, principal part, what is still owed afterwards.
    // monthlyRate is a fraction (1% per month = 0.01); LoanCalculatorService converts the yearly % once.
    List<ScheduleEntryResponse> buildSchedule(BigDecimal amount, BigDecimal monthlyRate, int termMonths);
}
