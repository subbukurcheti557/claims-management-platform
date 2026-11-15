package com.icmp.notification.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.notification.common.error.ApiException;
import com.icmp.notification.notification.domain.ClaimEvent;
import com.icmp.notification.notification.domain.Notification;
import com.icmp.notification.notification.domain.NotificationDraft;
import com.icmp.notification.notification.domain.NotificationTemplates;
import com.icmp.notification.notification.infrastructure.NotificationRepository;
import com.icmp.notification.notification.infrastructure.ProcessedEvent;
import com.icmp.notification.notification.infrastructure.ProcessedEventRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processedEvents;
    private final List<NotificationChannel> channels;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, ProcessedEventRepository processedEvents,
                               List<NotificationChannel> channels, Clock clock) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.channels = channels;
        this.clock = clock;
    }

    /**
     * Turns one claim event into notifications, exactly once per event id. The processed-event row and the
     * notifications commit together; a redelivered event finds its id and is skipped.
     */
    @Transactional
    public int handle(ClaimEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Event {} ({}) already processed, skipping", event.eventId(), event.eventType());
            return 0;
        }
        Instant now = Instant.now(clock);
        List<NotificationDraft> drafts = NotificationTemplates.render(event);
        for (NotificationDraft d : drafts) {
            Notification saved = notifications.save(new Notification(d.recipientUserId(), event.eventId(),
                    event.eventType(), d.category(), event.claimId(), event.claimNumber(), d.title(), d.message(),
                    now));
            channels.forEach(c -> c.deliver(saved));
        }
        processedEvents.save(new ProcessedEvent(event.eventId(), event.eventType(), now));
        return drafts.size();
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? notifications.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByRecipientUserIdAndReadAtIsNull(userId);
    }

    /** Users can only mark their own notifications; anyone else's is reported as not found. */
    @Transactional
    public Notification markRead(UUID notificationId, UUID userId) {
        Notification n = notifications.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification " + notificationId + " was not found."));
        n.markRead(Instant.now(clock));
        return n;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now(clock));
    }
}
