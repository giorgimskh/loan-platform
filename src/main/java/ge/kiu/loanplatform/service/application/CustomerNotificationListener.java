package ge.kiu.loanplatform.service.application;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Observer pattern: an observer of ApplicationStatusChangedEvent.
// It tells the customer about the new status. There is no mail server, so the message is written to the log.
@Component
@Slf4j
public class CustomerNotificationListener {

    // AFTER_COMMIT (the default phase): runs only when the status change was really saved,
    // so the customer is never told about a decision that was rolled back
    @TransactionalEventListener
    public void onStatusChanged(ApplicationStatusChangedEvent event) {
        log.info(buildMessage(event));
    }

    // Separate from the logging so a unit test can check the text
    String buildMessage(ApplicationStatusChangedEvent event) {
        String message = "To " + event.customerFullName() + " <" + event.customerEmail() + ">: ";
        if (event.newStatus() == ApplicationStatus.SUBMITTED) {
            return message + "your loan application #" + event.applicationId() + " was received";
        }
        message += "your loan application #" + event.applicationId() + " was " + event.newStatus();
        if (event.newStatus() == ApplicationStatus.REJECTED) {
            message += ". Reason: " + event.decisionComment();
        }
        return message;
    }
}
