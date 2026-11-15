package com.icmp.policy;

import static com.icmp.policy.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.icmp.policy.policy.infrastructure.PolicyRepository;
import com.icmp.policy.policy.infrastructure.ProcessedEventRepository;
import com.icmp.policy.support.PostgresTestConfig;
import com.icmp.policy.support.TestTokens;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"claims.lifecycle.v1", "claims.lifecycle.v1.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PolicyServiceIntegrationTest {

    private static final String TOPIC = "claims.lifecycle.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    PolicyRepository policies;

    @Autowired
    ProcessedEventRepository processedEvents;

    @Autowired
    KafkaTemplate<Object, Object> kafka;

    @Autowired
    EmbeddedKafkaBroker broker;

    // ---------- REST ----------

    @Test
    void claimantSeesOnlyOwnPolicies() throws Exception {
        mvc.perform(get("/api/policies/mine").header("Authorization", bearer(TestTokens.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].holderName").value("Alice Tan"));
    }

    @Test
    void staffCannotUseClaimantEndpoint() throws Exception {
        mvc.perform(get("/api/policies/mine").header("Authorization", bearer(TestTokens.olivia())))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignPolicyIsNotFoundForClaimantButVisibleToStaff() throws Exception {
        mvc.perform(get("/api/policies/SG-MOT-1001").header("Authorization", bearer(TestTokens.bob())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/policies/SG-MOT-1001").header("Authorization", bearer(TestTokens.olivia())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("SGD"));
    }

    @Test
    void requiresToken() throws Exception {
        mvc.perform(get("/api/policies/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void coverageCheckAcceptsValidIncident() throws Exception {
        mvc.perform(get("/internal/policies/SG-MOT-1001/coverage")
                        .param("claimantId", TestTokens.ALICE)
                        .param("incidentDate", LocalDate.now().minusDays(3).toString())
                        .param("claimType", "MOTOR")
                        .header("Authorization", bearer(TestTokens.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.covered").value(true))
                .andExpect(jsonPath("$.deductible").value(500.00))
                .andExpect(jsonPath("$.reasons.length()").value(0));
    }

    @Test
    void coverageCheckExplainsWhyLapsedPolicyIsNotCovered() throws Exception {
        mvc.perform(get("/internal/policies/SG-MOT-0901/coverage")
                        .param("claimantId", TestTokens.ALICE)
                        .param("incidentDate", LocalDate.now().minusDays(3).toString())
                        .param("claimType", "PROPERTY")
                        .header("Authorization", bearer(TestTokens.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.covered").value(false))
                .andExpect(jsonPath("$.reasons.length()").value(3));
    }

    @Test
    void coverageCheckForSomeoneElsesPolicyIsRejected() throws Exception {
        mvc.perform(get("/internal/policies/SG-MOT-1001/coverage")
                        .param("claimantId", TestTokens.BOB)
                        .param("incidentDate", LocalDate.now().toString())
                        .param("claimType", "MOTOR")
                        .header("Authorization", bearer(TestTokens.bob())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/policies/SG-MOT-1001/coverage")
                        .param("claimantId", TestTokens.ALICE)
                        .param("incidentDate", LocalDate.now().toString())
                        .param("claimType", "MOTOR")
                        .header("Authorization", bearer(TestTokens.bob())))
                .andExpect(status().isForbidden());
    }

    // ---------- Kafka ----------

    @Test
    void claimSettledIsAppliedExactlyOnceEvenIfDeliveredTwice() {
        BigDecimal before = policies.findById("HK-MOT-1002").orElseThrow().getPaidToDate();
        UUID eventId = UUID.randomUUID();
        String event = settledEvent(eventId, "HK-MOT-1002", "4500.00");

        kafka.send(TOPIC, "claim-1", event);
        kafka.send(TOPIC, "claim-1", event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(processedEvents.existsById(eventId)).isTrue());
        // give the duplicate time to be consumed, then verify it changed nothing
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(policies.findById("HK-MOT-1002").orElseThrow().getPaidToDate())
                        .isEqualByComparingTo(before.add(new BigDecimal("4500.00"))));
    }

    @Test
    void otherEventTypesAreIgnored() {
        BigDecimal before = policies.findById("MY-MOT-1004").orElseThrow().getPaidToDate();
        UUID eventId = UUID.randomUUID();
        kafka.send(TOPIC, "claim-2", "{\"eventId\":\"" + eventId + "\",\"eventType\":\"ClaimApproved\","
                + "\"policyNumber\":\"MY-MOT-1004\",\"data\":{\"amount\":100}}");
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(processedEvents.existsById(eventId)).isFalse();
            assertThat(policies.findById("MY-MOT-1004").orElseThrow().getPaidToDate()).isEqualByComparingTo(before);
        });
    }

    @Test
    void malformedEventGoesToDeadLetterTopic() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-check", "true", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, TOPIC + ".DLT");
            kafka.send(TOPIC, "bad", "{not json");
            ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(consumer, TOPIC + ".DLT",
                    Duration.ofSeconds(20));
            assertThat(dead.value()).isEqualTo("{not json");
        }
    }

    private static String settledEvent(UUID eventId, String policyNumber, String amount) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"ClaimSettled\",\"schemaVersion\":1,"
                + "\"claimId\":\"" + UUID.randomUUID() + "\",\"claimNumber\":\"CLM-HK-2026-000001\","
                + "\"policyNumber\":\"" + policyNumber + "\",\"futureField\":\"ignored\","
                + "\"data\":{\"amount\":" + amount + ",\"paymentReference\":\"PAY-1\"}}";
    }
}
