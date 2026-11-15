package com.icmp.notification.notification.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * notification-service's own view of a claims.lifecycle.v1 event (tolerant reader). Only the fields needed to
 * write notifications are mapped; anything else in the message is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimEvent(UUID eventId, String eventType, int schemaVersion, UUID claimId, String claimNumber,
                         UUID claimantId, UUID assignedOfficerId, String assignedOfficerName, String currency,
                         UUID actorId, JsonNode data) {

    public String text(String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).asText() : null;
    }

    public BigDecimal amount(String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).decimalValue() : null;
    }

    public String money(String field) {
        BigDecimal value = amount(field);
        return value == null ? "" : currency + " " + String.format("%,.2f", value);
    }
}
