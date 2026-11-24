package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for the observer: checks the text the customer would receive. No Spring, no mocks needed.
class CustomerNotificationListenerTest {

    private final CustomerNotificationListener listener = new CustomerNotificationListener();

    @Test
    void buildMessage_saysReceived_forANewApplication() {
        String message = listener.buildMessage(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", null, ApplicationStatus.SUBMITTED, null));

        assertThat(message).isEqualTo("To Ana Beridze <ana@example.com>: your loan application #5 was received");
    }

    @Test
    void buildMessage_namesTheDecision_whenApproved() {
        String message = listener.buildMessage(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED, null));

        assertThat(message).isEqualTo("To Ana Beridze <ana@example.com>: your loan application #5 was APPROVED");
    }

    @Test
    void buildMessage_includesTheReason_whenRejected() {
        String message = listener.buildMessage(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", ApplicationStatus.SUBMITTED, ApplicationStatus.REJECTED,
                "Income too low"));

        assertThat(message).isEqualTo(
                "To Ana Beridze <ana@example.com>: your loan application #5 was REJECTED. Reason: Income too low");
    }
}
