package com.icmp.claims.claim.infrastructure.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes unsent outbox rows to Kafka in creation order, keyed by claim id so all events for one claim land
 * on the same partition in order. Delivery is at-least-once: consumers de-duplicate by eventId.
 * If Kafka is unavailable the rows stay unsent and are retried on the next run.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafka,
                       @Value("${app.kafka.claims-topic}") String topic, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.topic = topic;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}",
            initialDelayString = "${app.outbox.initial-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic,
                        event.getAggregateId().toString(), event.getPayload());
                record.headers().add("eventType", event.getEventType().getBytes());
                record.headers().add("eventId", event.getId().toString().getBytes());
                kafka.send(record).get(10, TimeUnit.SECONDS);
                event.markPublished(Instant.now(clock));
            } catch (Exception e) {
                // stop at the first failure to keep per-claim ordering; retry next run
                event.markFailed(e.getMessage());
                log.warn("Could not publish outbox event {} ({}); will retry: {}", event.getId(),
                        event.getEventType(), e.getMessage());
                break;
            }
        }
        if (!batch.isEmpty()) {
            log.debug("Outbox relay processed {} event(s)", batch.size());
        }
    }
}
