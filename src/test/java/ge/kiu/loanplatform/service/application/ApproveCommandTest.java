package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Unit test for the "approve" command on its own: no Spring, no mocks, just a plain application object.
class ApproveCommandTest {

    @Test
    void execute_setsApprovedAndRemovesTheReason_whenThereAreNoProblems() {
        LoanApplication application = application(ApplicationStatus.REJECTED);
        application.setDecisionComment("Income too low");

        new ApproveCommand(List.of()).execute(application);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getDecisionComment()).isNull();
    }

    @Test
    void execute_throwsConflictAndChangesNothing_whenARuleFoundAProblem() {
        LoanApplication application = application(ApplicationStatus.SUBMITTED);

        assertThatThrownBy(() -> new ApproveCommand(List.of("required documents are missing: ID Card"))
                .execute(application))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Application with id 1 cannot be approved, required documents are missing: ID Card");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void targetStatus_isApproved() {
        assertThat(new ApproveCommand(List.of()).targetStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    private LoanApplication application(ApplicationStatus status) {
        LoanApplication application = new LoanApplication();
        application.setId(1L);
        application.setStatus(status);
        return application;
    }
}
