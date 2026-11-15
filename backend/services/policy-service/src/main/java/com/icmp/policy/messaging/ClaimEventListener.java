package com.icmp.policy.messaging;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icmp.policy.policy.application.ClaimPaymentRecorder;

/** Reacts to ClaimSettled by adding the paid amount to the policy. All other event types are ignored. */
@Component
public class ClaimEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventListener.class);
    static final String CLAIM_SETTLED = "ClaimSettled";

    private final ObjectMapper objectMapper;
    private final ClaimPaymentRecorder recorder;

    public ClaimEventListener(ObjectMapper objectMapper, ClaimPaymentRecorder recorder) {
        this.objectMapper = objectMapper;
        this.recorder = recorder;
    }

    @KafkaListener(topics = "${app.kafka.claims-topic}", groupId = "policy-service")
    public void onMessage(String payload) throws JsonProcessingException {
        ClaimLifecycleMessage event = objectMapper.readValue(payload, ClaimLifecycleMessage.class);
        if (!CLAIM_SETTLED.equals(event.eventType())) {
            return;
        }
        if (event.eventId() == null || event.policyNumber() == null || event.data() == null
                || !event.data().hasNonNull("amount")) {
            throw new IllegalArgumentException("ClaimSettled event is missing eventId, policyNumber or amount");
        }
        BigDecimal amount = event.data().get("amount").decimalValue();
        log.debug("Applying ClaimSettled {} for claim {}", event.eventId(), event.claimNumber());
        recorder.recordSettlement(event.eventId(), event.policyNumber(), event.claimNumber(), amount);
    }
}
