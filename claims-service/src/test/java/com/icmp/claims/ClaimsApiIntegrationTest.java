package com.icmp.claims;

import static com.icmp.claims.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icmp.claims.claim.application.port.PolicyCoveragePort;
import com.icmp.claims.claim.application.port.PolicyCoveragePort.Coverage;
import com.icmp.claims.claim.application.port.StaffDirectoryPort;
import com.icmp.claims.claim.application.port.StaffDirectoryPort.StaffMember;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.infrastructure.messaging.OutboxEventRepository;
import com.icmp.claims.common.error.ApiException;
import com.icmp.claims.support.PostgresTestConfig;
import com.icmp.claims.support.TestTokens;

/**
 * End-to-end through the HTTP API with a real PostgreSQL and an embedded Kafka broker. The two outbound REST
 * ports (policy-service, identity-service) are replaced by mocks so each scenario controls their answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"claims.lifecycle.v1"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimsApiIntegrationTest {

    private static final String TOPIC = "claims.lifecycle.v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    EmbeddedKafkaBroker broker;

    @MockitoBean
    PolicyCoveragePort policyCoverage;

    @MockitoBean
    StaffDirectoryPort staffDirectory;

    Consumer<String, String> consumer;

    @BeforeAll
    void subscribe() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("claims-it-" + UUID.randomUUID(), "false", broker);
        props.put("auto.offset.reset", "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterAll
    void close() {
        consumer.close();
    }

    @BeforeEach
    void coveredByDefault() {
        when(policyCoverage.checkCoverage(eq("SG-MOT-1001"), any(), any(), eq(ClaimType.MOTOR), anyString()))
                .thenReturn(Optional.of(new Coverage("SG-MOT-1001", true, List.of(), "SG", "SGD",
                        new BigDecimal("60000"), new BigDecimal("500"), new BigDecimal("56800"), "Toyota Corolla")));
        when(staffDirectory.findStaff(eq(UUID.fromString(TestTokens.OMAR)), anyString()))
                .thenReturn(Optional.of(new StaffMember(UUID.fromString(TestTokens.OMAR), "Omar Rahman",
                        "CLAIMS_OFFICER")));
        when(staffDirectory.findStaff(eq(UUID.fromString(TestTokens.ALICE)), anyString()))
                .thenReturn(Optional.of(new StaffMember(UUID.fromString(TestTokens.ALICE), "Alice Tan", "CLAIMANT")));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> fnol(String estimatedLoss) {
        return Map.of("policyNumber", "SG-MOT-1001", "claimType", "MOTOR",
                "incidentDate", LocalDate.now().minusDays(2).toString(), "incidentLocation", "Orchard Road",
                "description", "Rear-ended at the traffic lights on Orchard Road, bumper and boot damaged.",
                "injuryReported", false, "estimatedLoss", new BigDecimal(estimatedLoss));
    }

    private JsonNode submitClaim(String estimatedLoss) throws Exception {
        return body(postJson("/api/claims", TestTokens.alice(), fnol(estimatedLoss)).andExpect(status().isCreated()));
    }

    private List<ConsumerRecord<String, String>> eventsFor(String claimId, int expected) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).records(TOPIC).forEach(r -> {
                if (claimId.equals(r.key())) {
                    found.add(r);
                }
            });
            return found.size() >= expected;
        });
        return found;
    }

    private static String eventType(ConsumerRecord<String, String> record) {
        return new String(record.headers().lastHeader("eventType").value());
    }

    // ------------------------------------------------------------------ FNOL

    @Nested
    class Fnol {

        @Test
        void submitsClaimAndPublishesClaimSubmittedThroughTheOutbox() throws Exception {
            ResultActions result = postJson("/api/claims", TestTokens.alice(), fnol("8000"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.claimNumber").value(org.hamcrest.Matchers.startsWith("CLM-SG-")))
                    .andExpect(jsonPath("$.currency").value("SGD"))
                    // claimant view is redacted
                    .andExpect(jsonPath("$.reserveAmount").doesNotExist())
                    .andExpect(jsonPath("$.priority").doesNotExist())
                    .andExpect(jsonPath("$.allowedActions[0]").value("WITHDRAW"));
            String claimId = body(result).get("id").asText();

            List<ConsumerRecord<String, String>> events = eventsFor(claimId, 1);
            JsonNode event = json.readTree(events.get(0).value());
            assertThat(event.get("eventType").asText()).isEqualTo("ClaimSubmitted");
            assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(event.get("policyNumber").asText()).isEqualTo("SG-MOT-1001");
            assertThat(event.get("claimantId").asText()).isEqualTo(TestTokens.ALICE);
            assertThat(outbox.findByAggregateIdOrderByCreatedAtAsc(UUID.fromString(claimId)))
                    .allSatisfy(o -> assertThat(o.getPublishedAt()).isNotNull());
        }

        @Test
        void staffSeeReserveAndPriorityForTheNewClaim() throws Exception {
            String id = submitClaim("8000").get("id").asText();
            mvc.perform(get("/api/claims/" + id).header("Authorization", bearer(TestTokens.olivia())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserveAmount").value(7500.00))
                    .andExpect(jsonPath("$.priority").value("LOW"))
                    .andExpect(jsonPath("$.claimantName").value("Alice Tan"))
                    .andExpect(jsonPath("$.allowedActions[0]").value("ASSIGN"));
        }

        @Test
        void notCoveredIs422WithReasonAndNothingIsSaved() throws Exception {
            when(policyCoverage.checkCoverage(eq("SG-MOT-1001"), any(), any(), any(), anyString()))
                    .thenReturn(Optional.of(new Coverage("SG-MOT-1001", false,
                            List.of("Incident date is outside the policy period."), "SG", "SGD",
                            BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, "Car")));
            long before = outbox.count();
            postJson("/api/claims", TestTokens.alice(), fnol("8000"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Not covered"))
                    .andExpect(jsonPath("$.detail").value("Incident date is outside the policy period."));
            assertThat(outbox.count()).isEqualTo(before);
        }

        @Test
        void unknownPolicyIs404() throws Exception {
            when(policyCoverage.checkCoverage(eq("XX-1"), any(), any(), any(), anyString()))
                    .thenReturn(Optional.empty());
            Map<String, Object> req = new java.util.HashMap<>(fnol("100"));
            req.put("policyNumber", "XX-1");
            postJson("/api/claims", TestTokens.alice(), req).andExpect(status().isNotFound());
        }

        @Test
        void policyServiceOutageIs503() throws Exception {
            when(policyCoverage.checkCoverage(any(), any(), any(), any(), anyString()))
                    .thenThrow(ApiException.unavailable("We could not verify your policy right now."));
            postJson("/api/claims", TestTokens.alice(), fnol("8000"))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        void invalidReportReturnsFieldErrors() throws Exception {
            postJson("/api/claims", TestTokens.alice(), Map.of("policyNumber", "", "claimType", "MOTOR",
                    "incidentDate", LocalDate.now().plusDays(3).toString(), "incidentLocation", "x",
                    "description", "too short", "estimatedLoss", -5))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.length()").value(4));
        }

        @Test
        void staffCannotReportClaims() throws Exception {
            postJson("/api/claims", TestTokens.olivia(), fnol("100")).andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------ Visibility

    @Nested
    class Visibility {

        @Test
        void claimantSeesOnlyOwnClaims() throws Exception {
            mvc.perform(get("/api/claims/mine").header("Authorization", bearer(TestTokens.bob())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].claimNumber",
                            org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.startsWith("CLM-HK-"))));
        }

        @Test
        void foreignClaimIs404NotForbidden() throws Exception {
            // seeded claim 2 belongs to Bob
            mvc.perform(get("/api/claims/c0000000-0000-0000-0000-000000000002")
                            .header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void claimantCannotUseStaffEndpoints() throws Exception {
            mvc.perform(get("/api/claims/queue").header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/claims/stats/exposure").header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/claims/c0000000-0000-0000-0000-000000000001/notes")
                            .header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void requestsWithoutTokenAre401() throws Exception {
            mvc.perform(get("/api/claims/mine")).andExpect(status().isUnauthorized());
        }

        @Test
        void unassignedQueueIsSortedBySlaAndOnlyHoldsSubmittedClaims() throws Exception {
            JsonNode page = body(mvc.perform(get("/api/claims/queue").param("scope", "UNASSIGNED")
                    .header("Authorization", bearer(TestTokens.olivia()))).andExpect(status().isOk()));
            List<String> due = new ArrayList<>();
            page.get("content").forEach(c -> {
                assertThat(c.get("status").asText()).isEqualTo("SUBMITTED");
                assertThat(c.has("assignedOfficerId")).isFalse();
                due.add(c.get("slaDueAt").asText());
            });
            assertThat(due).isNotEmpty();
            assertThat(due).isSortedAccordingTo((a, b) -> java.time.Instant.parse(a).compareTo(java.time.Instant.parse(b)));
        }

        @Test
        void queueFiltersByMarketAndOverdue() throws Exception {
            mvc.perform(get("/api/claims/queue").param("scope", "TEAM").param("market", "TH")
                            .header("Authorization", bearer(TestTokens.maya())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].market", org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is("TH"))));
            mvc.perform(get("/api/claims/queue").param("scope", "TEAM").param("overdueOnly", "true")
                            .header("Authorization", bearer(TestTokens.maya())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].overdue", org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is(true))));
        }
    }

    // ------------------------------------------------------------------ Workflow

    @Nested
    class Workflow {

        @Test
        void fullLifecycleFromReportToPaymentPublishesEveryEventInOrder() throws Exception {
            JsonNode claim = submitClaim("8000");
            String id = claim.get("id").asText();
            String base = "/api/claims/" + id;

            JsonNode picked = body(postJson(base + "/assign", TestTokens.olivia(), Map.of("expectedVersion", 0))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.assignedOfficerName").value("Olivia Lim")));

            JsonNode asked = body(postJson(base + "/info-requests", TestTokens.olivia(),
                    Map.of("expectedVersion", picked.get("version").asLong(),
                            "question", "Please send the police report number."))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INFO_REQUESTED")));
            String requestId = asked.get("infoRequests").get(0).get("id").asText();

            postJson(base + "/info-requests/" + requestId + "/response", TestTokens.alice(),
                    Map.of("response", "PR-2026-77812"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.infoRequests[0].status").value("ANSWERED"));

            postJson(base + "/notes", TestTokens.olivia(), Map.of("body", "Called the workshop, quote is fair."))
                    .andExpect(status().isCreated());

            postJson(base + "/assessment", TestTokens.olivia(), Map.of("liability", "ACCEPTED",
                    "recommendedAmount", 7200, "reserveAmount", 7200, "findings", "Police report and photos match."))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ASSESSED"))
                    .andExpect(jsonPath("$.reserveAmount").value(7200))
                    .andExpect(jsonPath("$.noteCount").value(1));

            postJson(base + "/approve", TestTokens.olivia(), Map.of("amount", 7200))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            postJson(base + "/settle", TestTokens.olivia(), Map.of("paymentReference", "PAY-88231"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SETTLED"))
                    .andExpect(jsonPath("$.paidAmount").value(7200))
                    .andExpect(jsonPath("$.reserveAmount").value(0));

            // claimant sees the decision, but not internal fields
            mvc.perform(get(base).header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(jsonPath("$.status").value("SETTLED"))
                    .andExpect(jsonPath("$.paymentReference").value("PAY-88231"))
                    .andExpect(jsonPath("$.latestAssessment").doesNotExist());

            mvc.perform(get(base + "/timeline").header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(jsonPath("$[*].toStatus").value(org.hamcrest.Matchers.contains("SUBMITTED",
                            "UNDER_REVIEW", "INFO_REQUESTED", "UNDER_REVIEW", "ASSESSED", "APPROVED", "SETTLED")));

            List<String> types = eventsFor(id, 7).stream().map(ClaimsApiIntegrationTest::eventType).toList();
            assertThat(types).containsExactly("ClaimSubmitted", "ClaimAssigned", "InfoRequested", "InfoProvided",
                    "ClaimAssessed", "ClaimApproved", "ClaimSettled");
        }

        @Test
        void secondOfficerWithStaleVersionGets409() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            postJson("/api/claims/" + id + "/assign", TestTokens.olivia(), Map.of("expectedVersion", 0))
                    .andExpect(status().isOk());
            postJson("/api/claims/" + id + "/assign", TestTokens.omar(), Map.of("expectedVersion", 0))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Concurrent modification"));
        }

        @Test
        void officerCannotActOnColleaguesClaim() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            postJson("/api/claims/" + id + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            postJson("/api/claims/" + id + "/info-requests", TestTokens.omar(), Map.of("question", "Any photos?"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void invalidTransitionIs409() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            postJson("/api/claims/" + id + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            postJson("/api/claims/" + id + "/settle", TestTokens.olivia(), Map.of("paymentReference", "PAY-1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Action not allowed in current status"));
        }

        @Test
        void officerOverAuthorityLimitIsRefusedAndManagerApproves() throws Exception {
            // SGD 45,000 = about USD 33,300, above the officer limit of USD 25,000
            String id = submitClaim("50000").get("id").asText();
            String base = "/api/claims/" + id;
            postJson(base + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            postJson(base + "/assessment", TestTokens.olivia(), Map.of("liability", "ACCEPTED",
                    "recommendedAmount", 45000, "reserveAmount", 45000, "findings", "Total loss confirmed by assessor."))
                    .andExpect(status().isOk());
            postJson(base + "/approve", TestTokens.olivia(), Map.of("amount", 45000))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("authority limit")));
            postJson(base + "/approve", TestTokens.maya(), Map.of("amount", 45000))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        void managerAssignsToAnotherOfficerAfterDirectoryCheck() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            postJson("/api/claims/" + id + "/assign", TestTokens.maya(), Map.of("officerId", TestTokens.OMAR))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignedOfficerName").value("Omar Rahman"));
        }

        @Test
        void claimsCannotBeAssignedToNonStaff() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            postJson("/api/claims/" + id + "/assign", TestTokens.maya(), Map.of("officerId", TestTokens.ALICE))
                    .andExpect(status().isUnprocessableEntity());
            postJson("/api/claims/" + id + "/assign", TestTokens.olivia(), Map.of("officerId", TestTokens.OMAR))
                    .andExpect(status().isForbidden());
        }

        @Test
        void rejectionCarriesReasonToClaimant() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            String base = "/api/claims/" + id;
            postJson(base + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            postJson(base + "/reject", TestTokens.olivia(), Map.of("reasonCode", "POLICY_EXCLUSION",
                    "explanation", "Mechanical breakdown is excluded under section 4."))
                    .andExpect(status().isOk());
            mvc.perform(get(base).header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReasonCode").value("POLICY_EXCLUSION"))
                    .andExpect(jsonPath("$.decisionNote").value("Mechanical breakdown is excluded under section 4."));
        }

        @Test
        void claimantWithdrawsSubmittedClaim() throws Exception {
            String id = submitClaim("2000").get("id").asText();
            postJson("/api/claims/" + id + "/withdraw", TestTokens.alice(), Map.of("reason", "Repaired it myself"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                    .andExpect(jsonPath("$.allowedActions.length()").value(0));
            postJson("/api/claims/" + id + "/withdraw", TestTokens.bob(), Map.of()).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------ Dashboards

    @Nested
    class Dashboards {

        @Test
        void exposureTotalsAddUpAcrossMarkets() throws Exception {
            JsonNode exposure = body(mvc.perform(get("/api/claims/stats/exposure")
                    .header("Authorization", bearer(TestTokens.maya()))).andExpect(status().isOk()));
            JsonNode totals = exposure.get("totals");
            BigDecimal outstanding = totals.get("outstandingUsd").decimalValue();
            BigDecimal paid = totals.get("paidUsd").decimalValue();
            assertThat(totals.get("incurredUsd").decimalValue()).isEqualByComparingTo(outstanding.add(paid));

            BigDecimal byMarket = BigDecimal.ZERO;
            long openByMarket = 0;
            for (JsonNode m : exposure.get("byMarket")) {
                byMarket = byMarket.add(m.get("outstandingUsd").decimalValue());
                openByMarket += m.get("openClaims").asLong();
            }
            assertThat(byMarket).isEqualByComparingTo(outstanding);
            assertThat(openByMarket).isEqualTo(totals.get("openClaims").asLong());
            assertThat(exposure.get("byMarket")).hasSize(6);
            assertThat(exposure.get("ageing")).hasSize(4);
            assertThat(totals.get("overdueClaims").asLong()).isGreaterThan(0);
        }

        @Test
        void exposureCanBeFilteredByMarket() throws Exception {
            mvc.perform(get("/api/claims/stats/exposure").param("market", "TH")
                            .header("Authorization", bearer(TestTokens.maya())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.byMarket.length()").value(1))
                    .andExpect(jsonPath("$.byMarket[0].currency").value("THB"));
        }

        @Test
        void workloadListsOfficersWithOpenAndClosedFigures() throws Exception {
            JsonNode workload = body(mvc.perform(get("/api/claims/stats/workload")
                    .header("Authorization", bearer(TestTokens.maya()))).andExpect(status().isOk()));
            assertThat(workload.get("unassignedClaims").asLong()).isGreaterThan(0);
            List<String> names = new ArrayList<>();
            workload.get("officers").forEach(o -> names.add(o.get("officerName").asText()));
            assertThat(names).contains("Olivia Lim", "Omar Rahman");

            mvc.perform(get("/api/claims/stats/me").header("Authorization", bearer(TestTokens.omar())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.officerName").value("Omar Rahman"))
                    .andExpect(jsonPath("$.overdueClaims").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }
    }
}
