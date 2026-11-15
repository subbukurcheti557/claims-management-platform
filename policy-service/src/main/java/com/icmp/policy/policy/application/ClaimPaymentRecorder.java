package com.icmp.policy.policy.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.policy.policy.domain.Policy;
import com.icmp.policy.policy.infrastructure.PolicyRepository;
import com.icmp.policy.policy.infrastructure.ProcessedEvent;
import com.icmp.policy.policy.infrastructure.ProcessedEventRepository;

/**
 * Applies a settled claim payment to the policy's paid-to-date exactly once per event id. The processed-event
 * row and the policy update commit in one local transaction, so a redelivered event is a no-op.
 */
@Service
public class ClaimPaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClaimPaymentRecorder.class);

    private final PolicyRepository policies;
    private final ProcessedEventRepository processedEvents;

    public ClaimPaymentRecorder(PolicyRepository policies, ProcessedEventRepository processedEvents) {
        this.policies = policies;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public boolean recordSettlement(UUID eventId, String policyNumber, String claimNumber, BigDecimal amount) {
        if (processedEvents.existsById(eventId)) {
            log.info("Event {} already applied, skipping", eventId);
            return false;
        }
        Policy policy = policies.findById(policyNumber).orElseThrow(() ->
                new IllegalStateException("ClaimSettled for unknown policy " + policyNumber));
        policy.recordPayment(amount);
        processedEvents.save(new ProcessedEvent(eventId, "ClaimSettled"));
        log.info("Recorded payment {} for claim {} against policy {}; paid to date now {}", amount, claimNumber,
                policyNumber, policy.getPaidToDate());
        return true;
    }
}
