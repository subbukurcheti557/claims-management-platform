package com.icmp.notification.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.icmp.notification.notification.domain.Notification;

/** Stand-in for an email gateway: logs what would be sent. Replace with an SMTP or provider adapter. */
@Component
public class LoggingEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailChannel.class);

    @Override
    public void deliver(Notification n) {
        log.info("[email stub] to user {}: {} - {}", n.getRecipientUserId(), n.getTitle(), n.getMessage());
    }
}
