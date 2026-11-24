package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for the "reject" command on its own: no Spring, no mocks.
class RejectCommandTest {

    @Test
    void execute_setsRejectedAndSavesTheReasonFromTheCommand() {
        LoanApplication application = new LoanApplication();
        application.setStatus(ApplicationStatus.SUBMITTED);

        new RejectCommand("Income too low").execute(application);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getDecisionComment()).isEqualTo("Income too low");
    }

    @Test
    void targetStatus_isRejected() {
        assertThat(new RejectCommand("Income too low").targetStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }
}
