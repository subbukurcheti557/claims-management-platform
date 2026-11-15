package com.icmp.claims.claim.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Something that happened to a claim. The aggregate records these as it changes; the application layer turns
 * them into outbox rows in the same transaction.
 */
public record ClaimDomainEvent(UUID eventId, String eventType, Instant occurredAt, UUID actorId,
                               Map<String, Object> data) {

    public static final String SUBMITTED = "ClaimSubmitted";
    public static final String ASSIGNED = "ClaimAssigned";
    public static final String INFO_REQUESTED = "InfoRequested";
    public static final String INFO_PROVIDED = "InfoProvided";
    public static final String ASSESSED = "ClaimAssessed";
    public static final String APPROVED = "ClaimApproved";
    public static final String SETTLED = "ClaimSettled";
    public static final String REJECTED = "ClaimRejected";
    public static final String WITHDRAWN = "ClaimWithdrawn";

    public static ClaimDomainEvent of(String type, Actor actor, Map<String, Object> data) {
        return new ClaimDomainEvent(UUID.randomUUID(), type, Instant.now(), actor.id(), data);
    }
}
