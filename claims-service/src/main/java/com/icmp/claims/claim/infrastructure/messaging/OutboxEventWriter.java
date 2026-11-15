package com.icmp.claims.claim.infrastructure.messaging;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icmp.claims.claim.application.port.EventOutboxPort;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimDomainEvent;

/**
 * Builds the claims.lifecycle.v1 envelope (see contracts/events/claims.lifecycle.v1.schema.json) and stores it in
 * the outbox. Requires an existing transaction: the event must commit or roll back together with the claim.
 */
@Component
public class OutboxEventWriter implements EventOutboxPort {

    public static final int SCHEMA_VERSION = 1;

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Claim claim, List<ClaimDomainEvent> events) {
        for (ClaimDomainEvent event : events) {
            repository.save(new OutboxEvent(event.eventId(), claim.getId(), event.eventType(),
                    toJson(envelope(claim, event)), event.occurredAt()));
        }
    }

    Map<String, Object> envelope(Claim claim, ClaimDomainEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("eventType", event.eventType());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("claimId", claim.getId());
        envelope.put("claimNumber", claim.getClaimNumber());
        envelope.put("policyNumber", claim.getPolicyNumber());
        envelope.put("claimantId", claim.getClaimantId());
        envelope.put("assignedOfficerId", claim.getAssignedOfficerId());
        envelope.put("assignedOfficerName", claim.getAssignedOfficerName());
        envelope.put("market", claim.getMarket().name());
        envelope.put("currency", claim.getCurrency());
        envelope.put("status", claim.getStatus().name());
        envelope.put("actorId", event.actorId());
        envelope.put("data", event.data());
        return envelope;
    }

    private String toJson(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise claim event", e);
        }
    }
}
