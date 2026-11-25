package ge.kiu.loanplatform.entity.application;

import ge.kiu.loanplatform.entity.customer.Customer;
import ge.kiu.loanplatform.entity.product.LoanProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for the static factory methods (LoanApplication.submit, StatusChange.from)
// and the memento methods of the originator (LoanApplication). Plain objects, no Spring.
class LoanApplicationTest {

    @Test
    void submit_createsASubmittedApplicationWithTheGivenData() {
        Customer customer = new Customer();
        LoanProduct product = new LoanProduct();

        LoanApplication application = LoanApplication.submit(customer, product, new BigDecimal("5000.00"), 12);

        assertThat(application.getCustomer()).isSameAs(customer);
        assertThat(application.getLoanProduct()).isSameAs(product);
        assertThat(application.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(application.getTermMonths()).isEqualTo(12);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED); // rule 3
        assertThat(application.getDecisionComment()).isNull();
    }

    @Test
    void submit_startsTheHistoryWithOneSubmittedLine() {
        LoanApplication application = LoanApplication.submit(new Customer(), new LoanProduct(), BigDecimal.TEN, 6);

        assertThat(application.getStatusHistory()).hasSize(1);
        StatusChange first = application.getStatusHistory().getFirst();
        assertThat(first.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(first.getLoanApplication()).isSameAs(application);
    }

    @Test
    void statusChange_from_copiesTheCurrentStateWithoutAddingItself() {
        LoanApplication application = new LoanApplication();
        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecisionComment("Income too low");

        StatusChange change = StatusChange.from(application);

        assertThat(change.getLoanApplication()).isSameAs(application);
        assertThat(change.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(change.getDecisionComment()).isEqualTo("Income too low");
        assertThat(application.getStatusHistory()).isEmpty(); // the caller adds it
    }

    @Test
    void restore_bringsBackTheStateFromTheMemento() {
        LoanApplication application = new LoanApplication();
        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecisionComment("Income too low");
        ApplicationStateMemento snapshot = application.createMemento();

        application.setStatus(ApplicationStatus.APPROVED);
        application.setDecisionComment(null);
        application.restore(snapshot);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getDecisionComment()).isEqualTo("Income too low");
    }

    @Test
    void createMemento_isNotChangedByLaterChanges() {
        LoanApplication application = new LoanApplication();
        application.setStatus(ApplicationStatus.SUBMITTED);
        ApplicationStateMemento snapshot = application.createMemento();

        application.setStatus(ApplicationStatus.APPROVED);

        assertThat(snapshot.status()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void statusChange_toMemento_returnsTheSavedState() {
        StatusChange change = new StatusChange();
        change.setStatus(ApplicationStatus.REJECTED);
        change.setDecisionComment("Income too low");

        assertThat(change.toMemento())
                .isEqualTo(new ApplicationStateMemento(ApplicationStatus.REJECTED, "Income too low"));
    }
}
