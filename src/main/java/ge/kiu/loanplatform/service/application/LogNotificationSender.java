package ge.kiu.loanplatform.service.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// The NotificationSender used now: there is no mail server, so the message is written to the log.
// It is the only implementation, so Spring injects it wherever a NotificationSender is asked for.
@Component
@Slf4j
public class LogNotificationSender implements NotificationSender {

    @Override
    public void send(String recipient, String text) {
        log.info("To {}: {}", recipient, text);
    }
}
