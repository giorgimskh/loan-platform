package ge.kiu.loanplatform.entity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for the State pattern: what each status allows. No Spring, no mocks.
class ApplicationStatusTest {

    @Test
    void submitted_canBeModifiedAndDecidedButHasNoScheduleAndNothingToUndo() {
        assertThat(ApplicationStatus.SUBMITTED.canBeModified()).isTrue();
        assertThat(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.APPROVED)).isTrue();
        assertThat(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.SUBMITTED)).isFalse();
        assertThat(ApplicationStatus.SUBMITTED.hasRepaymentSchedule()).isFalse();
        assertThat(ApplicationStatus.SUBMITTED.hasDecisionToUndo()).isFalse();
    }

    @Test
    void approved_canOnlyBeChangedToRejectedAndHasSchedule() {
        assertThat(ApplicationStatus.APPROVED.canBeModified()).isFalse();
        assertThat(ApplicationStatus.APPROVED.canMoveTo(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.APPROVED.canMoveTo(ApplicationStatus.APPROVED)).isFalse();
        assertThat(ApplicationStatus.APPROVED.canMoveTo(ApplicationStatus.SUBMITTED)).isFalse();
        assertThat(ApplicationStatus.APPROVED.hasRepaymentSchedule()).isTrue();
        assertThat(ApplicationStatus.APPROVED.hasDecisionToUndo()).isTrue();
    }

    @Test
    void rejected_canOnlyBeChangedToApprovedAndHasNoSchedule() {
        assertThat(ApplicationStatus.REJECTED.canBeModified()).isFalse();
        assertThat(ApplicationStatus.REJECTED.canMoveTo(ApplicationStatus.APPROVED)).isTrue();
        assertThat(ApplicationStatus.REJECTED.canMoveTo(ApplicationStatus.REJECTED)).isFalse();
        assertThat(ApplicationStatus.REJECTED.canMoveTo(ApplicationStatus.SUBMITTED)).isFalse();
        assertThat(ApplicationStatus.REJECTED.hasRepaymentSchedule()).isFalse();
        assertThat(ApplicationStatus.REJECTED.hasDecisionToUndo()).isTrue();
    }

    @Test
    void allowedNextStatuses_listsWhereADecisionCanStillMoveTheApplication() {
        assertThat(ApplicationStatus.SUBMITTED.allowedNextStatuses())
                .containsExactly(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
        assertThat(ApplicationStatus.APPROVED.allowedNextStatuses()).containsExactly(ApplicationStatus.REJECTED);
        assertThat(ApplicationStatus.REJECTED.allowedNextStatuses()).containsExactly(ApplicationStatus.APPROVED);
    }

    @Test
    void everyStatus_describesWhatItAllows() {
        assertThat(ApplicationStatus.SUBMITTED.describeAllowedActions())
                .isEqualTo("A SUBMITTED application is waiting for a decision: it can be modified, cancelled, approved or rejected.");
        assertThat(ApplicationStatus.APPROVED.describeAllowedActions())
                .isEqualTo("An APPROVED application can only be rejected or have its decision undone.");
        assertThat(ApplicationStatus.REJECTED.describeAllowedActions())
                .isEqualTo("A REJECTED application can only be approved or have its decision undone.");
    }
}
