package ge.kiu.loanplatform.service.application.file;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.file.ApplicationPartVisitor;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Visitor pattern: one operation on an application file, "write it as plain text for the archive".
 * Every visit method writes the lines for one kind of part; getText() returns the finished file.
 *
 * Not a Spring bean: it collects text while it visits, so a new visitor is made for every file
 * (one shared instance would mix the files of two requests).
 */
public class TextExportVisitor implements ApplicationPartVisitor {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StringBuilder text = new StringBuilder();
    // documents and history lines come one by one; the section title is written before the first one
    private boolean documentsStarted = false;
    private boolean historyStarted = false;

    @Override
    public void visitApplication(LoanApplicationResponse application) {
        line("LOAN APPLICATION FILE #" + application.id());
        line("Status: " + application.status());
        if (application.decisionComment() != null) {
            line("Rejection reason: " + application.decisionComment());
        }
        line("Amount: " + money(application.amount()) + " for " + application.termMonths() + " months");
        line("Created: " + dateTime(application.createdAt()));
        line("Documents uploaded: " + application.documents().size());
    }

    @Override
    public void visitCustomer(CustomerResponse customer) {
        section("CUSTOMER");
        line("Name: " + customer.fullName());
        line("Email: " + customer.email());
        line("Personal number: " + customer.personalNumber());
        line("Phone: " + (customer.phone() != null ? customer.phone() : "not given"));
    }

    @Override
    public void visitProduct(LoanProductResponse product) {
        section("LOAN PRODUCT");
        line("Name: " + product.name());
        line("Interest: " + product.interestRate() + "% per year");
        line("Payments: " + product.repaymentType().getLabel());
        List<String> required = new ArrayList<>();
        for (DocumentTypeResponse documentType : product.requiredDocuments()) {
            required.add(documentType.name());
        }
        line("Required documents: " + (required.isEmpty() ? "none" : String.join(", ", required)));
    }

    @Override
    public void visitDocument(DocumentResponse document) {
        if (!documentsStarted) {
            section("DOCUMENTS");
            documentsStarted = true;
        }
        line("- " + document.documentTypeName() + ": " + document.originalFileName()
                + " (uploaded " + dateTime(document.uploadedAt()) + ")");
    }

    @Override
    public void visitHistoryLine(StatusChangeResponse historyLine) {
        if (!historyStarted) {
            section("HISTORY");
            historyStarted = true;
        }
        String reason = historyLine.decisionComment() != null ? ": " + historyLine.decisionComment() : "";
        line("- " + dateTime(historyLine.changedAt()) + " " + historyLine.status() + reason);
    }

    // the whole file, after the visitor went through every part
    public String getText() {
        return text.toString();
    }

    // ---------- helpers ----------

    private void section(String title) {
        text.append('\n');
        line(title);
    }

    private void line(String value) {
        text.append(value).append('\n');
    }

    // 7000 -> "7,000.00"; Locale.ROOT so the result does not depend on the server's language settings
    private String money(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,.2f", amount);
    }

    private String dateTime(LocalDateTime value) {
        return value == null ? "unknown" : value.format(DATE_TIME);
    }
}
