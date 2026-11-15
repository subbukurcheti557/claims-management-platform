package com.icmp.notification;

import static com.icmp.notification.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.icmp.notification.notification.infrastructure.NotificationRepository;
import com.icmp.notification.notification.infrastructure.ProcessedEventRepository;
import com.icmp.notification.support.PostgresTestConfig;
import com.icmp.notification.support.TestTokens;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"claims.lifecycle.v1", "claims.lifecycle.v1.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class NotificationServiceIntegrationTest {

    private static final String TOPIC = "claims.lifecycle.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaTemplate<Object, Object> kafka;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    ProcessedEventRepository processedEvents;

    private static String event(UUID eventId, String type, String claimantId, String officerId, String actorId,
                                String data) {
        return """
                {"eventId":"%s","eventType":"%s","schemaVersion":1,"occurredAt":"2026-11-15T10:00:00Z",
                 "claimId":"c0000000-0000-0000-0000-0000000000ff","claimNumber":"CLM-HK-2026-000999",
                 "policyNumber":"HK-MOT-1002","claimantId":"%s","assignedOfficerId":%s,
                 "assignedOfficerName":"Olivia Lim","market":"HK","currency":"HKD","status":"X","actorId":"%s",
                 "data":%s}""".formatted(eventId, type, claimantId, officerId == null ? "null" : "\"" + officerId + "\"",
                actorId, data);
    }

    @Test
    void eventBecomesNotificationForTheClaimantExactlyOnce() throws Exception {
        long before = notifications.countByRecipientUserIdAndReadAtIsNull(UUID.fromString(TestTokens.BOB));
        UUID eventId = UUID.randomUUID();
        String payload = event(eventId, "ClaimApproved", TestTokens.BOB, TestTokens.OLIVIA, TestTokens.OLIVIA,
                "{\"amount\":12000}");
        kafka.send(TOPIC, "claim", payload);
        kafka.send(TOPIC, "claim", payload); // duplicate delivery

        await().atMost(Duration.ofSeconds(20)).until(() -> processedEvents.existsById(eventId));
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notifications.countByEventId(eventId)).isEqualTo(1));

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(TestTokens.bob())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(before + 1));
        mvc.perform(get("/api/notifications").param("unreadOnly", "true")
                        .header("Authorization", bearer(TestTokens.bob())))
                .andExpect(jsonPath("$.content[0].title").value("Claim approved"))
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("HKD 12,000.00")))
                .andExpect(jsonPath("$.content[0].category").value("DECISION"));
    }

    @Test
    void infoProvidedReachesTheOfficerNotTheClaimant() {
        UUID eventId = UUID.randomUUID();
        kafka.send(TOPIC, "claim", event(eventId, "InfoProvided", TestTokens.BOB, TestTokens.OMAR, TestTokens.BOB,
                "{\"requestId\":\"x\"}"));
        await().atMost(Duration.ofSeconds(20)).until(() -> processedEvents.existsById(eventId));
        assertThat(notifications.findAll()).filteredOn(n -> n.getEventId().equals(eventId))
                .singleElement()
                .satisfies(n -> assertThat(n.getRecipientUserId()).isEqualTo(UUID.fromString(TestTokens.OMAR)));
    }

    @Test
    void usersReadAndMarkOnlyTheirOwnNotifications() throws Exception {
        // seeded: Alice has one unread (claim received) and one read; Tara has one unread
        String aliceUnread = "a0000000-0000-0000-0000-000000000003";
        String taraUnread = "a0000000-0000-0000-0000-000000000001";

        mvc.perform(post("/api/notifications/" + taraUnread + "/read")
                        .header("Authorization", bearer(TestTokens.alice())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + aliceUnread + "/read")
                        .header("Authorization", bearer(TestTokens.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(TestTokens.alice())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(TestTokens.alice())))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }
}
