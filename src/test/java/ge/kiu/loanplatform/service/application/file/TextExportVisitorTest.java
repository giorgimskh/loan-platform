package ge.kiu.loanplatform.service.application.file;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.file.ApplicationFile;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of the visitor pattern with the text export: no Spring, no mocks.
// An ApplicationFile is built from plain records, the visitor goes through it, and the text is checked.
class TextExportVisitorTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 20, 9, 15);
    private static final LocalDateTime UPLOADED = LocalDateTime.of(2026, 11, 20, 9, 30);
    private static final LocalDateTime DECIDED = LocalDateTime.of(2026, 11, 21, 16, 0);

    @Test
    void getText_writesEveryPartOfTheFileInOrder() {
        TextExportVisitor visitor = new TextExportVisitor();

        approvedFile().accept(visitor);

        assertThat(visitor.getText()).isEqualTo("""
                LOAN APPLICATION FILE #5
                Status: APPROVED
                Amount: 7,000.00 for 24 months
                Created: 2026-11-20 09:15
                Documents uploaded: 1

                CUSTOMER
                Name: Ana Beridze
                Email: ana@mail.com
                Personal number: 01234567890
                Phone: 555123456

                LOAN PRODUCT
                Name: Consumer Loan
                Interest: 14.50% per year
                Payments: Equal monthly payments
                Required documents: ID Card

                DOCUMENTS
                - ID Card: passport.pdf (uploaded 2026-11-20 09:30)

                HISTORY
                - 2026-11-20 09:15 SUBMITTED
                - 2026-11-21 16:00 APPROVED
                """);
    }

    @Test
    void getText_showsTheRejectionReason_andLeavesOutSectionsWithoutParts() {
        TextExportVisitor visitor = new TextExportVisitor();
        LoanApplicationResponse rejected = new LoanApplicationResponse(6L, new BigDecimal("500"), 12,
                ApplicationStatus.REJECTED, CREATED, "Income too low", 1L, "Ana Beridze", 1L, "Consumer Loan", List.of());
        CustomerResponse noPhone = new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", null);

        new ApplicationFile(rejected, noPhone, product(), List.of(
                new StatusChangeResponse(ApplicationStatus.SUBMITTED, null, CREATED),
                new StatusChangeResponse(ApplicationStatus.REJECTED, "Income too low", DECIDED)))
                .accept(visitor);

        String text = visitor.getText();
        assertThat(text).contains("Rejection reason: Income too low");
        assertThat(text).contains("Phone: not given");
        assertThat(text).contains("Documents uploaded: 0");
        assertThat(text).doesNotContain("DOCUMENTS");     // no document, so no documents section
        assertThat(text).contains("- 2026-11-21 16:00 REJECTED: Income too low");
    }

    // ---------- sample parts ----------

    private ApplicationFile approvedFile() {
        DocumentResponse passport = new DocumentResponse(10L, "passport.pdf", 1L, "ID Card", UPLOADED);
        LoanApplicationResponse application = new LoanApplicationResponse(5L, new BigDecimal("7000"), 24,
                ApplicationStatus.APPROVED, CREATED, null, 1L, "Ana Beridze", 1L, "Consumer Loan", List.of(passport));
        CustomerResponse customer = new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456");
        return new ApplicationFile(application, customer, product(), List.of(
                new StatusChangeResponse(ApplicationStatus.SUBMITTED, null, CREATED),
                new StatusChangeResponse(ApplicationStatus.APPROVED, null, DECIDED)));
    }

    private LoanProductResponse product() {
        return new LoanProductResponse(1L, "Consumer Loan", new BigDecimal("14.50"), new BigDecimal("500"),
                new BigDecimal("20000"), 3, 48, RepaymentType.ANNUITY, List.of(new DocumentTypeResponse(1L, "ID Card")));
    }
}
