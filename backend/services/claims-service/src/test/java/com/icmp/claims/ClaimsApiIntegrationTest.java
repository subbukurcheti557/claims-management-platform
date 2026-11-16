package com.icmp.claims;

import static com.icmp.claims.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.mock.web.MockMultipartFile;
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
import com.icmp.claims.support.ObjectStorageTestConfig;
import com.icmp.claims.support.PostgresTestConfig;
import com.icmp.claims.support.TestTokens;

/**
 * End-to-end through the HTTP API with a real PostgreSQL, real S3-compatible storage and an embedded Kafka broker. The two outbound REST
 * ports (policy-service, identity-service) are replaced by mocks so each scenario controls their answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, ObjectStorageTestConfig.class})
@EmbeddedKafka(partitions = 1, topics = {"claims.lifecycle.v1"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimsApiIntegrationTest {

    private static final String TOPIC = "claims.lifecycle.v1";

    /** Seed claim CLM-SG-2026-000009 on SG-MOT-1001 is SETTLED, with an incident 61 days ago. */
    private static final int SEEDED_PAID_CLAIM_DAYS_AGO = 61;

    private static final AtomicInteger DAYS_AGO = new AtomicInteger(2);

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

    /**
     * Each new claim gets its own incident date, so tests do not trip the duplicate-claim rule on one another.
     * Days 2 and 61 are skipped: the seed data already has claims on SG-MOT-1001 for those incident dates.
     */
    private static LocalDate uniqueIncidentDate() {
        int daysAgo = DAYS_AGO.incrementAndGet();
        while (daysAgo == 2 || daysAgo == SEEDED_PAID_CLAIM_DAYS_AGO) {
            daysAgo = DAYS_AGO.incrementAndGet();
        }
        return LocalDate.now().minusDays(daysAgo);
    }

    private Map<String, Object> fnol(String estimatedLoss) {
        return fnol("SG-MOT-1001", estimatedLoss, uniqueIncidentDate());
    }

    private Map<String, Object> fnol(String policyNumber, String estimatedLoss, LocalDate incidentDate) {
        return Map.of("policyNumber", policyNumber, "claimType", "MOTOR",
                "incidentDate", incidentDate.toString(), "incidentLocation", "Orchard Road",
                "description", "Rear-ended at the traffic lights on Orchard Road, bumper and boot damaged.",
                "injuryReported", false, "estimatedLoss", new BigDecimal(estimatedLoss));
    }

    /** Smallest valid JPEG header plus padding: recognised as a JPEG by its first bytes. */
    static final byte[] JPEG = jpeg(2048);

    static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 0x4A, 0x46, 0x49, 0x46};
        System.arraycopy(header, 0, bytes, 0, header.length);
        for (int i = header.length; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    private ResultActions upload(String claimId, String token, String category, String fileName, String contentType,
                                 byte[] content) throws Exception {
        return mvc.perform(multipart("/api/claims/" + claimId + "/documents")
                .file(new MockMultipartFile("file", fileName, contentType, content))
                .param("category", category)
                .header("Authorization", bearer(token)));
    }

    /** The claimant adds a damage photo, which approval requires. */
    private JsonNode uploadPhoto(String claimId) throws Exception {
        return body(upload(claimId, TestTokens.alice(), "DAMAGE_PHOTO", "rear-bumper.jpg", "image/jpeg", JPEG)
                .andExpect(status().isCreated()));
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
            uploadPhoto(id);

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

            List<String> types = eventsFor(id, 8).stream().map(ClaimsApiIntegrationTest::eventType).toList();
            assertThat(types).containsExactly("ClaimSubmitted", "ClaimAssigned", "InfoRequested", "InfoProvided",
                    "DocumentUploaded", "ClaimAssessed", "ClaimApproved", "ClaimSettled");
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
            uploadPhoto(id);
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

    // ------------------------------------------------------------------ Duplicates and the policy limit

    @Nested
    class DuplicatesAndPolicyLimit {

        @Test
        void sameIncidentWhileTheFirstClaimIsOpenIs409UntilItIsWithdrawn() throws Exception {
            LocalDate day = uniqueIncidentDate();
            JsonNode first = body(postJson("/api/claims", TestTokens.alice(), fnol("SG-MOT-1001", "3000", day))
                    .andExpect(status().isCreated()));
            String firstNumber = first.get("claimNumber").asText();

            postJson("/api/claims", TestTokens.alice(), fnol("SG-MOT-1001", "3000", day))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Duplicate claim"))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                            "Claim " + firstNumber + " for policy SG-MOT-1001 and incident date " + day)));

            // a different day on the same policy is a different incident
            postJson("/api/claims", TestTokens.alice(), fnol("SG-MOT-1001", "3000", uniqueIncidentDate()))
                    .andExpect(status().isCreated());

            // once the first claim is withdrawn the incident can be reported again
            postJson("/api/claims/" + first.get("id").asText() + "/withdraw", TestTokens.alice(), Map.of())
                    .andExpect(status().isOk());
            postJson("/api/claims", TestTokens.alice(), fnol("SG-MOT-1001", "3000", day))
                    .andExpect(status().isCreated());
        }

        @Test
        void sameIncidentAsAPaidClaimIsAcceptedButFlaggedForStaff() throws Exception {
            LocalDate paidIncident = LocalDate.now().minusDays(SEEDED_PAID_CLAIM_DAYS_AGO);
            String id = body(postJson("/api/claims", TestTokens.alice(), fnol("SG-MOT-1001", "3000", paidIncident))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.possibleDuplicateOf").doesNotExist())).get("id").asText();

            mvc.perform(get("/api/claims/" + id).header("Authorization", bearer(TestTokens.olivia())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.possibleDuplicateOf").value("CLM-SG-2026-000009"))
                    .andExpect(jsonPath("$.priority").value("HIGH"));
            mvc.perform(get("/api/claims/" + id).header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(jsonPath("$.possibleDuplicateOf").doesNotExist());
        }

        @Test
        void approvalsAcrossClaimsCannotTakeThePolicyOverItsSumInsured() throws Exception {
            // a dedicated policy so other tests' approvals do not count: SGD 10,000 sum insured, nothing paid
            when(policyCoverage.checkCoverage(eq("SG-MOT-7001"), any(), any(), eq(ClaimType.MOTOR), anyString()))
                    .thenReturn(Optional.of(new Coverage("SG-MOT-7001", true, List.of(), "SG", "SGD",
                            new BigDecimal("10000"), new BigDecimal("500"), new BigDecimal("10000"), "Honda Jazz")));
            String first = assessed("SG-MOT-7001", "6000");
            String second = assessed("SG-MOT-7001", "6000");

            postJson("/api/claims/" + first + "/approve", TestTokens.olivia(), Map.of("amount", 6000))
                    .andExpect(status().isOk());
            // each claim alone is within cover, but together they would pay SGD 12,000
            postJson("/api/claims/" + second + "/approve", TestTokens.olivia(), Map.of("amount", 6000))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                            "only SGD 4000.00 of the SGD 10000.00 sum insured is left")));
            postJson("/api/claims/" + second + "/approve", TestTokens.olivia(), Map.of("amount", 4000))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        private String assessed(String policyNumber, String amount) throws Exception {
            String id = body(postJson("/api/claims", TestTokens.alice(),
                    fnol(policyNumber, amount, uniqueIncidentDate())).andExpect(status().isCreated()))
                    .get("id").asText();
            postJson("/api/claims/" + id + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            postJson("/api/claims/" + id + "/assessment", TestTokens.olivia(), Map.of("liability", "ACCEPTED",
                    "recommendedAmount", new BigDecimal(amount), "reserveAmount", new BigDecimal(amount),
                    "findings", "Repair quote checked."))
                    .andExpect(status().isOk());
            uploadPhoto(id);
            return id;
        }
    }

    // ------------------------------------------------------------------ Documents (accident photos)

    @Nested
    class Documents {

        @Test
        void claimantUploadsAPhotoAndStaffGetTheSameBytesBack() throws Exception {
            String id = submitClaim("4000").get("id").asText();

            JsonNode doc = body(upload(id, TestTokens.alice(), "DAMAGE_PHOTO", "IMG_2041.JPEG", "image/jpeg", JPEG)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/content")))
                    .andExpect(jsonPath("$.category").value("DAMAGE_PHOTO"))
                    .andExpect(jsonPath("$.categoryLabel").value("Damage photo"))
                    .andExpect(jsonPath("$.fileName").value("IMG_2041.jpg"))
                    .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                    .andExpect(jsonPath("$.sizeBytes").value(JPEG.length))
                    .andExpect(jsonPath("$.uploadedByName").value("Alice Tan"))
                    .andExpect(jsonPath("$.sha256").doesNotExist()));

            mvc.perform(get("/api/claims/" + id + "/documents").header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sha256").doesNotExist());
            mvc.perform(get("/api/claims/" + id + "/documents").header("Authorization", bearer(TestTokens.olivia())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sha256").value(org.hamcrest.Matchers.hasLength(64)));

            byte[] downloaded = mvc.perform(get(doc.get("contentUrl").asText())
                            .header("Authorization", bearer(TestTokens.olivia())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/jpeg"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(downloaded).isEqualTo(JPEG);

            List<ConsumerRecord<String, String>> events = eventsFor(id, 2);
            JsonNode uploaded = json.readTree(events.get(1).value());
            assertThat(uploaded.get("eventType").asText()).isEqualTo("DocumentUploaded");
            assertThat(uploaded.get("status").asText()).isEqualTo("SUBMITTED");
            assertThat(uploaded.get("data").get("category").asText()).isEqualTo("DAMAGE_PHOTO");
            assertThat(uploaded.get("data").get("uploadedByRole").asText()).isEqualTo("CLAIMANT");
            // a document is not a status change: the claim's version stays the same
            mvc.perform(get("/api/claims/" + id).header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        void filesThatAreNotPhotosOrPdfsAre415AndNothingIsStored() throws Exception {
            String id = submitClaim("4000").get("id").asText();
            byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
            byte[] pdf = "%PDF-1.7\nreport".getBytes(StandardCharsets.ISO_8859_1);

            // the browser says image/jpeg and the name ends in .jpg, but the content decides
            upload(id, TestTokens.alice(), "DAMAGE_PHOTO", "photo.jpg", "image/jpeg", html)
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.title").value("Unsupported file"));
            upload(id, TestTokens.alice(), "DAMAGE_PHOTO", "scan.pdf", "application/pdf", pdf)
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.detail").value("Damage photos must be JPEG, PNG or WebP images."));
            upload(id, TestTokens.alice(), "POLICE_REPORT", "scan.pdf", "application/pdf", pdf)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contentType").value("application/pdf"));
            upload(id, TestTokens.alice(), "DAMAGE_PHOTO", "empty.jpg", "image/jpeg", new byte[0])
                    .andExpect(status().isUnprocessableEntity());
            upload(id, TestTokens.alice(), "DAMAGE_PHOTO", "huge.jpg", "image/jpeg", jpeg(10 * 1024 * 1024 + 1))
                    .andExpect(status().isPayloadTooLarge());
            upload(id, TestTokens.alice(), "SELFIE", "a.jpg", "image/jpeg", JPEG)
                    .andExpect(status().isBadRequest());

            mvc.perform(get("/api/claims/" + id + "/documents").header("Authorization", bearer(TestTokens.olivia())))
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void otherClaimantsCannotSeeOrAddDocuments() throws Exception {
            String id = submitClaim("4000").get("id").asText();
            JsonNode doc = uploadPhoto(id);

            mvc.perform(get("/api/claims/" + id + "/documents").header("Authorization", bearer(TestTokens.bob())))
                    .andExpect(status().isNotFound());
            mvc.perform(get(doc.get("contentUrl").asText()).header("Authorization", bearer(TestTokens.bob())))
                    .andExpect(status().isNotFound());
            upload(id, TestTokens.bob(), "DAMAGE_PHOTO", "a.jpg", "image/jpeg", JPEG)
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/claims/" + id + "/documents")).andExpect(status().isUnauthorized());
        }

        @Test
        void staffDocumentsAreInternalAndOnlyTheHandlerCanAddThem() throws Exception {
            String id = submitClaim("4000").get("id").asText();
            byte[] pdf = "%PDF-1.7\nassessor report".getBytes(StandardCharsets.ISO_8859_1);

            // not picked up yet: an officer cannot add to it
            upload(id, TestTokens.olivia(), "OTHER", "assessor.pdf", "application/pdf", pdf)
                    .andExpect(status().isForbidden());
            postJson("/api/claims/" + id + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            JsonNode internal = body(upload(id, TestTokens.olivia(), "OTHER", "assessor.pdf", "application/pdf", pdf)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.uploadedByRole").value("CLAIMS_OFFICER")));
            upload(id, TestTokens.omar(), "OTHER", "x.pdf", "application/pdf", pdf).andExpect(status().isForbidden());

            mvc.perform(get("/api/claims/" + id + "/documents").header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(jsonPath("$.length()").value(0));
            mvc.perform(get(internal.get("contentUrl").asText()).header("Authorization", bearer(TestTokens.alice())))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/claims/" + id + "/documents").header("Authorization", bearer(TestTokens.omar())))
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void approvalNeedsADamagePhotoAndTheRefusalChangesNothing() throws Exception {
            String id = submitClaim("4000").get("id").asText();
            String base = "/api/claims/" + id;
            postJson(base + "/assign", TestTokens.olivia(), Map.of()).andExpect(status().isOk());
            postJson(base + "/assessment", TestTokens.olivia(), Map.of("liability", "ACCEPTED",
                    "recommendedAmount", 3500, "reserveAmount", 3500, "findings", "Workshop quote looks fair."))
                    .andExpect(status().isOk());
            long version = body(mvc.perform(get(base).header("Authorization", bearer(TestTokens.olivia()))))
                    .get("version").asLong();

            postJson(base + "/approve", TestTokens.olivia(), Map.of("amount", 3500))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("has no damage photos")));
            mvc.perform(get(base).header("Authorization", bearer(TestTokens.olivia())))
                    .andExpect(jsonPath("$.status").value("ASSESSED"))
                    .andExpect(jsonPath("$.version").value(version));

            // a police report is not a photo of the damage
            upload(id, TestTokens.alice(), "POLICE_REPORT", "report.pdf", "application/pdf",
                    "%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1)).andExpect(status().isCreated());
            postJson(base + "/approve", TestTokens.olivia(), Map.of("amount", 3500))
                    .andExpect(status().isUnprocessableEntity());

            uploadPhoto(id);
            postJson(base + "/approve", TestTokens.olivia(), Map.of("amount", 3500))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
            // after the decision the claimant can no longer add evidence
            upload(id, TestTokens.alice(), "DAMAGE_PHOTO", "late.jpg", "image/jpeg", JPEG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("because it is APPROVED")));
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
