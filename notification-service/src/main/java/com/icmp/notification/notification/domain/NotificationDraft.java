package com.icmp.notification.notification.domain;

import java.util.UUID;

/** A notification a template wants to send, before it is stored or delivered. */
public record NotificationDraft(UUID recipientUserId, Notification.Category category, String title, String message) {
}
