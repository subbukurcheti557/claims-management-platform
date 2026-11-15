package com.icmp.notification.notification.application;

import com.icmp.notification.notification.domain.Notification;

/** A delivery channel (Strategy). In-app is the stored record itself; email/SMS would be further channels. */
public interface NotificationChannel {

    void deliver(Notification notification);
}
