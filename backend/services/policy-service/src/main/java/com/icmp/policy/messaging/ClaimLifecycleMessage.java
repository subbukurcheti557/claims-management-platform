package com.icmp.policy.messaging;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This consumer's own view of the claims.lifecycle.v1 event (tolerant reader): only the fields policy-service
 * needs. Unknown fields are ignored, so producers can add fields without breaking this service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimLifecycleMessage(UUID eventId, String eventType, int schemaVersion, UUID claimId,
                                    String claimNumber, String policyNumber, JsonNode data) {
}
