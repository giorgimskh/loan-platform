package ge.kiu.loanplatform.entity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for the memento methods of the originator (LoanApplication). Plain objects, no Spring.
class LoanApplicationTest {

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
