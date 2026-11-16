package ge.kiu.loanplatform.dto;

import java.math.BigDecimal;

// One month of a repayment schedule: payment = interest + principal
public record ScheduleEntryResponse(
        int month,
        BigDecimal payment,
        BigDecimal interest,          // the bank's part of this payment
        BigDecimal principal,         // the part that pays back the loan itself
        BigDecimal remainingBalance   // what the customer still owes after this payment
) {
}
