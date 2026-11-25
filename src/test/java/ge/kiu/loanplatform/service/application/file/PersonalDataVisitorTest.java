package ge.kiu.loanplatform.service.application.file;

import ge.kiu.loanplatform.dto.application.LoanApplicationResponse;
import ge.kiu.loanplatform.dto.application.StatusChangeResponse;
import ge.kiu.loanplatform.dto.customer.CustomerResponse;
import ge.kiu.loanplatform.dto.document.DocumentResponse;
import ge.kiu.loanplatform.dto.document.DocumentTypeResponse;
import ge.kiu.loanplatform.dto.file.ApplicationFile;
import ge.kiu.loanplatform.dto.file.PersonalDataItem;
import ge.kiu.loanplatform.dto.product.LoanProductResponse;
import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.product.RepaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test of the second visitor: no Spring, no mocks.
// It goes through the same kind of ApplicationFile as TextExportVisitorTest.
class PersonalDataVisitorTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 20, 9, 15);
    private static final LocalDateTime DECIDED = LocalDateTime.of(2026, 11, 21, 16, 0);

    @Test
    void getItems_listsEveryPlaceWithPersonalData_inTheOrderOfTheFile() {
        PersonalDataVisitor visitor = new PersonalDataVisitor();

        rejectedFile().accept(visitor);

        assertThat(visitor.getItems()).containsExactly(
                new PersonalDataItem("Application 6", "Rejection reason", "Income too low"),
                new PersonalDataItem("Customer", "Name", "Ana Beridze"),
                new PersonalDataItem("Customer", "Email", "ana@mail.com"),
                new PersonalDataItem("Customer", "Personal number", "01234567890"),
                new PersonalDataItem("Customer", "Phone", "555123456"),
                new PersonalDataItem("Document 10 (ID Card)", "File name", "ana-beridze-passport.pdf"),
                new PersonalDataItem("History 2026-11-21 16:00", "Rejection reason", "Income too low"));
    }

    @Test
    void getItems_leavesOutEmptyValuesAndTheProduct() {
        PersonalDataVisitor visitor = new PersonalDataVisitor();
        LoanApplicationResponse submitted = new LoanApplicationResponse(7L, new BigDecimal("500"), 12,
                ApplicationStatus.SUBMITTED, CREATED, null, 1L, "Ana Beridze", 1L, "Consumer Loan", List.of());
        CustomerResponse noPhone = new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", null);

        new ApplicationFile(submitted, noPhone, product(),
                List.of(new StatusChangeResponse(ApplicationStatus.SUBMITTED, null, CREATED)))
                .accept(visitor);

        // no rejection reason, no phone, no documents, and a product holds nothing personal:
        // only the three customer details are left
        assertThat(visitor.getItems()).extracting(PersonalDataItem::field)
                .containsExactly("Name", "Email", "Personal number");
    }

    // ---------- sample parts ----------

    private ApplicationFile rejectedFile() {
        DocumentResponse passport = new DocumentResponse(10L, "ana-beridze-passport.pdf", 1L, "ID Card", CREATED);
        LoanApplicationResponse application = new LoanApplicationResponse(6L, new BigDecimal("500"), 12,
                ApplicationStatus.REJECTED, CREATED, "Income too low", 1L, "Ana Beridze", 1L, "Consumer Loan",
                List.of(passport));
        CustomerResponse customer = new CustomerResponse(1L, "Ana Beridze", "ana@mail.com", "01234567890", "555123456");
        return new ApplicationFile(application, customer, product(), List.of(
                new StatusChangeResponse(ApplicationStatus.SUBMITTED, null, CREATED),
                new StatusChangeResponse(ApplicationStatus.REJECTED, "Income too low", DECIDED)));
    }

    private LoanProductResponse product() {
        return new LoanProductResponse(1L, "Consumer Loan", new BigDecimal("14.50"), new BigDecimal("500"),
                new BigDecimal("20000"), 3, 48, RepaymentType.ANNUITY, List.of(new DocumentTypeResponse(1L, "ID Card")));
    }
}
