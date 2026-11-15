package com.icmp.notification.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One in-app notification for one user. Created from a claim event; only {@code readAt} ever changes. */
@Entity
@Table(name = "notification")
public class Notification {

    public enum Category {
        INFO,
        ACTION_REQUIRED,
        DECISION
    }

    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "claim_number", nullable = false)
    private String claimNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID recipientUserId, UUID eventId, String eventType, Category category, UUID claimId,
                        String claimNumber, String title, String message, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.recipientUserId = recipientUserId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.category = category;
        this.claimId = claimId;
        this.claimNumber = claimNumber;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Category getCategory() {
        return category;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
