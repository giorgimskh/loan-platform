package ge.kiu.loanplatform.service.application.event;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.service.notification.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Observer pattern: an observer of ApplicationStatusChangedEvent.
// It writes the message for the customer and hands it to a NotificationSender, which delivers it.
// The listener decides WHAT to say; the sender decides HOW it reaches the customer.
@Component
@RequiredArgsConstructor
public class CustomerNotificationListener {

    private final NotificationSender notificationSender;

    // AFTER_COMMIT (the default phase): runs only when the status change was really saved,
    // so the customer is never told about a decision that was rolled back
    @TransactionalEventListener
    public void onStatusChanged(ApplicationStatusChangedEvent event) {
        String recipient = event.customerFullName() + " <" + event.customerEmail() + ">";
        notificationSender.send(recipient, buildMessage(event));
    }

    // Separate from sending so a unit test can check the text
    String buildMessage(ApplicationStatusChangedEvent event) {
        if (event.newStatus() == ApplicationStatus.SUBMITTED) {
            return "your loan application #" + event.applicationId() + " was received";
        }
        String message = "your loan application #" + event.applicationId() + " was " + event.newStatus();
        if (event.newStatus() == ApplicationStatus.REJECTED) {
            message += ". Reason: " + event.decisionComment();
        }
        return message;
    }
}
