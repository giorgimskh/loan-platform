package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.entity.application.LoanApplication;
import ge.kiu.loanplatform.entity.document.DocumentType;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Unit test for the "approve" command on its own: no Spring, no mocks, just a plain application object.
class ApproveCommandTest {

    @Test
    void execute_setsApprovedAndRemovesTheReason_whenNoDocumentIsMissing() {
        LoanApplication application = application(ApplicationStatus.REJECTED);
        application.setDecisionComment("Income too low");

        new ApproveCommand(List.of()).execute(application);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getDecisionComment()).isNull();
    }

    @Test
    void execute_throwsConflictAndChangesNothing_whenADocumentIsMissing() { // rule 7
        LoanApplication application = application(ApplicationStatus.SUBMITTED);
        DocumentType idCard = new DocumentType();
        idCard.setName("ID Card");

        assertThatThrownBy(() -> new ApproveCommand(List.of(idCard)).execute(application))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ID Card");
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
