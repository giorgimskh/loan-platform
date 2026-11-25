package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// Unit test for the observer: checks the text the customer would receive and that it is handed to the sender.
// The NotificationSender is a mock, so nothing is really logged or sent.
@ExtendWith(MockitoExtension.class)
class CustomerNotificationListenerTest {

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private CustomerNotificationListener listener;

    @Test
    void onStatusChanged_sendsTheMessageToTheCustomer() {
        listener.onStatusChanged(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED, null));

        verify(notificationSender).send("Ana Beridze <ana@example.com>", "your loan application #5 was APPROVED");
    }

    @Test
    void buildMessage_saysReceived_forANewApplication() {
        String message = listener.buildMessage(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", null, ApplicationStatus.SUBMITTED, null));

        assertThat(message).isEqualTo("your loan application #5 was received");
    }

    @Test
    void buildMessage_namesTheDecision_whenApproved() {
        String message = listener.buildMessage(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED, null));

        assertThat(message).isEqualTo("your loan application #5 was APPROVED");
    }

    @Test
    void buildMessage_includesTheReason_whenRejected() {
        String message = listener.buildMessage(new ApplicationStatusChangedEvent(
                5L, "Ana Beridze", "ana@example.com", ApplicationStatus.SUBMITTED, ApplicationStatus.REJECTED,
                "Income too low"));

        assertThat(message).isEqualTo("your loan application #5 was REJECTED. Reason: Income too low");
    }
}
