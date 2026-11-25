package ge.kiu.loanplatform.dto.file;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;

// Visitor pattern: the "visitor". One operation on an application file is one class
// that implements this interface, with one method per kind of part.
// A new operation (e.g. a CSV export) = a new class; the parts do not change.
// The compiler makes every visitor handle every kind of part.
public interface ApplicationPartVisitor {

    void visitApplication(LoanApplicationResponse application);

    void visitCustomer(CustomerResponse customer);

    void visitProduct(LoanProductResponse product);

    void visitDocument(DocumentResponse document);

    void visitHistoryLine(StatusChangeResponse historyLine);
}
