package ge.kiu.loanplatform.dto.file;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;

import java.util.List;

// Visitor pattern: the "object structure". Everything the bank keeps about one application,
// made of parts of different types. Its accept(...) takes a visitor through every part
// in a fixed order, so a visitor never has to know how the file is put together.
// The documents come from application.documents(), so they are not stored twice.
public record ApplicationFile(
        LoanApplicationResponse application,
        CustomerResponse customer,
        LoanProductResponse product,
        List<StatusChangeResponse> history    // oldest first
) {

    public void accept(ApplicationPartVisitor visitor) {
        application.accept(visitor);
        customer.accept(visitor);
        product.accept(visitor);
        for (DocumentResponse document : application.documents()) {
            document.accept(visitor);
        }
        for (StatusChangeResponse historyLine : history) {
            historyLine.accept(visitor);
        }
    }
}
