package ge.kiu.loanplatform.dto;

import java.util.List;

// The repayment plan of an approved application: the totals and one line per month
public record RepaymentScheduleResponse(
        Long applicationId,
        PaymentCalculationResponse summary,
        List<ScheduleEntryResponse> payments
) {
}
