package ge.kiu.loanplatform.service.application;

// How a message reaches a customer. It says nothing about the channel (log, e-mail, SMS).
// Today LogNotificationSender only writes the message to the log because there is no mail server;
// an e-mail sender could be added later as another class that implements this interface,
// and CustomerNotificationListener would not change (it only knows this interface).
public interface NotificationSender {

    // recipient = who gets it, e.g. "Ana Beridze <ana@example.com>"; text = the message itself
    void send(String recipient, String text);
}
