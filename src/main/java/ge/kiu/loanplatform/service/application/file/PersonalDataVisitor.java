package ge.kiu.loanplatform.service.application.file;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.file.ApplicationPartVisitor;
import ge.kiu.loanplatform.dto.file.PersonalDataItem;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor pattern: a second operation on the same application file,
 * "where do we store personal data about this person?" (a question data protection rules like GDPR ask).
 *
 * It goes through exactly the same parts as TextExportVisitor, and none of the parts had to change
 * for it: that is what the visitor pattern is for. Each visit method collects the personal data
 * of one kind of part; getItems() returns the list.
 *
 * Not a Spring bean: it collects items while it visits, so a new visitor is made for every file.
 */
public class PersonalDataVisitor implements ApplicationPartVisitor {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<PersonalDataItem> items = new ArrayList<>();

    // The rejection reason is free text an officer typed, so it may describe the person
    @Override
    public void visitApplication(LoanApplicationResponse application) {
        add("Application " + application.id(), "Rejection reason", application.decisionComment());
    }

    @Override
    public void visitCustomer(CustomerResponse customer) {
        add("Customer", "Name", customer.fullName());
        add("Customer", "Email", customer.email());
        add("Customer", "Personal number", customer.personalNumber());
        add("Customer", "Phone", customer.phone());
    }

    // A loan product is the same for every customer: nothing personal in it
    @Override
    public void visitProduct(LoanProductResponse product) {
    }

    // The original file name was chosen by the customer and often contains a name
    @Override
    public void visitDocument(DocumentResponse document) {
        add("Document " + document.id() + " (" + document.documentTypeName() + ")", "File name",
                document.originalFileName());
    }

    // Every rejection is kept in the history, also after the decision was changed or undone
    @Override
    public void visitHistoryLine(StatusChangeResponse historyLine) {
        String when = historyLine.changedAt() == null ? "unknown time" : historyLine.changedAt().format(DATE_TIME);
        add("History " + when, "Rejection reason", historyLine.decisionComment());
    }

    // everything found, in the order the file was visited
    public List<PersonalDataItem> getItems() {
        return items;
    }

    // empty values are not stored data, so they are not listed (e.g. no phone, no rejection reason)
    private void add(String source, String field, String value) {
        if (value != null && !value.isBlank()) {
            items.add(new PersonalDataItem(source, field, value));
        }
    }
}
