package ge.kiu.loanplatform.entity.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// How a loan is paid back. Each value has its own RepaymentStrategy (strategy pattern),
// which LoanCalculatorService picks by this value.
// It lives next to LoanProduct because a product says which type its loans use.
@Getter
@RequiredArgsConstructor
public enum RepaymentType {

    ANNUITY("Equal monthly payments"),             // the same payment every month
    DIFFERENTIATED("Decreasing monthly payments"); // the same part of the loan every month, plus interest on what is left

    // the name people see on the website (getLabel() comes from @Getter)
    private final String label;
}
