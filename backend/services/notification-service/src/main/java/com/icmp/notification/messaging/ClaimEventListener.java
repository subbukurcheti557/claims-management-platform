package com.icmp.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icmp.notification.notification.application.NotificationService;
import com.icmp.notification.notification.domain.ClaimEvent;

@Component
public class ClaimEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationService service;

    public ClaimEventListener(ObjectMapper objectMapper, NotificationService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.claims-topic}", groupId = "notification-service")
    public void onMessage(String payload) throws JsonProcessingException {
        ClaimEvent event = objectMapper.readValue(payload, ClaimEvent.class);
        if (event.eventId() == null || event.eventType() == null || event.claimId() == null) {
            throw new IllegalArgumentException("Claim event is missing eventId, eventType or claimId");
        }
        int created = service.handle(event);
        log.debug("{} for {} produced {} notification(s)", event.eventType(), event.claimNumber(), created);
    }
}
