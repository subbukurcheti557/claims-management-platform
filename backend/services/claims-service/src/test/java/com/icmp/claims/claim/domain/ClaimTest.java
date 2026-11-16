package com.icmp.claims.claim.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.icmp.claims.claim.domain.ClaimRuleException.Kind;

class ClaimTest {

    static final Actor ALICE = new Actor(UUID.randomUUID(), "Alice", Actor.CLAIMANT);
    static final Actor BOB = new Actor(UUID.randomUUID(), "Bob", Actor.CLAIMANT);
    static final Actor OLIVIA = new Actor(UUID.randomUUID(), "Olivia", Actor.CLAIMS_OFFICER);
    static final Actor OMAR = new Actor(UUID.randomUUID(), "Omar", Actor.CLAIMS_OFFICER);
    static final Actor MAYA = new Actor(UUID.randomUUID(), "Maya", Actor.CLAIMS_MANAGER);
    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    // SGD to USD at 0.74; officer limit USD 25,000 => SGD 33,783
    static final AuthorityPolicy AUTHORITY = new AuthorityPolicy(new BigDecimal("25000"),
            (amount, currency) -> amount.multiply(new BigDecimal("0.74")));

    Claim claim;

    @BeforeEach
    void setUp() {
        claim = submit(new BigDecimal("8000"));
    }

    static Claim submit(BigDecimal estimatedLoss) {
        return Claim.submit("CLM-SG-2026-000100",
                new Claim.NewClaim("SG-MOT-1001", ClaimType.MOTOR, LocalDate.of(2026, 11, 10), "Orchard Road",
                        "Rear-ended at the traffic lights", false, estimatedLoss),
                new Claim.CoverageSnapshot(Market.SG, "SGD", new BigDecimal("60000"), new BigDecimal("500"), "Car"),
                Priority.LOW, ALICE, NOW);
    }

    static List<String> eventTypes(Claim claim) {
        return claim.pullEvents().stream().map(ClaimDomainEvent::eventType).toList();
    }

    @Nested
    class Fnol {

        @Test
        void createsSubmittedClaimWithReserveSlaAndAuditEntry() {
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("7500"); // 8000 - 500 deductible
            assertThat(claim.getSlaDueAt()).isEqualTo(NOW.plus(Priority.LOW.sla()));
            assertThat(claim.getHistory()).singleElement()
                    .satisfies(h -> assertThat(h.getToStatus()).isEqualTo(ClaimStatus.SUBMITTED));
            assertThat(eventTypes(claim)).containsExactly(ClaimDomainEvent.SUBMITTED);
        }

        @Test
        void possibleDuplicateIsFlaggedAndTriagedHigh() {
            Claim repeat = Claim.submit("CLM-SG-2026-000101",
                    new Claim.NewClaim("SG-MOT-1001", ClaimType.MOTOR, LocalDate.of(2026, 11, 10), "Orchard Road",
                            "Rear-ended at the traffic lights", false, new BigDecimal("8000")),
                    new Claim.CoverageSnapshot(Market.SG, "SGD", new BigDecimal("52800"), new BigDecimal("500"), "Car",
                            new BigDecimal("60000")),
                    Priority.LOW, ALICE, NOW, "CLM-SG-2026-000100");
            assertThat(repeat.getPossibleDuplicateOf()).isEqualTo("CLM-SG-2026-000100");
            assertThat(repeat.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(repeat.getSlaDueAt()).isEqualTo(NOW.plus(Priority.HIGH.sla()));
            assertThat(repeat.getPolicySumInsured()).isEqualByComparingTo("60000");
            assertThat(claim.getPossibleDuplicateOf()).isNull();
        }

        @Test
        void initialReserveIsCappedBySumInsuredAndNeverNegative() {
            assertThat(Claim.initialReserve(new BigDecimal("90000"), new BigDecimal("60000"), new BigDecimal("500")))
                    .isEqualByComparingTo("59500");
            assertThat(Claim.initialReserve(new BigDecimal("300"), new BigDecimal("60000"), new BigDecimal("500")))
                    .isEqualByComparingTo("0");
        }

        @Test
        void onlyClaimantsCanReport() {
            assertThatThrownBy(() -> Claim.submit("X", new Claim.NewClaim("P", ClaimType.MOTOR, LocalDate.now(), "L",
                            "description", false, BigDecimal.TEN),
                    new Claim.CoverageSnapshot(Market.SG, "SGD", BigDecimal.TEN, BigDecimal.ONE, "Car"),
                    Priority.LOW, OLIVIA, NOW))
                    .isInstanceOf(ClaimRuleException.class)
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);
        }

        @Test
        void incidentDateCannotBeInTheFuture() {
            assertThatThrownBy(() -> Claim.submit("X", new Claim.NewClaim("P", ClaimType.MOTOR,
                            LocalDate.now().plusDays(1), "L", "description", false, BigDecimal.TEN),
                    new Claim.CoverageSnapshot(Market.SG, "SGD", BigDecimal.TEN, BigDecimal.ONE, "Car"),
                    Priority.LOW, ALICE, NOW))
                    .hasMessageContaining("future");
        }
    }

    @Nested
    class Assignment {

        @Test
        void officerPicksUpSubmittedClaim() {
            claim.pullEvents();
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
            assertThat(claim.getAssignedOfficerId()).isEqualTo(OLIVIA.id());
            assertThat(claim.pullEvents()).singleElement().satisfies(e -> {
                assertThat(e.eventType()).isEqualTo(ClaimDomainEvent.ASSIGNED);
                assertThat(e.data()).containsEntry("reassigned", false);
            });
        }

        @Test
        void officerCannotAssignSomeoneElse() {
            assertThatThrownBy(() -> claim.assign(OMAR.id(), OMAR.name(), OLIVIA, NOW))
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);
        }

        @Test
        void claimantCannotPickUp() {
            assertThatThrownBy(() -> claim.assign(ALICE.id(), ALICE.name(), ALICE, NOW))
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);
        }

        @Test
        void secondOfficerCannotTakeAnAssignedClaim() {
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            assertThatThrownBy(() -> claim.assign(OMAR.id(), OMAR.name(), OMAR, NOW))
                    .isInstanceOf(ClaimRuleException.class)
                    .extracting("kind").isEqualTo(Kind.INVALID_TRANSITION);
        }

        @Test
        void managerReassignsWithoutChangingStatus() {
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            claim.pullEvents();
            claim.assign(OMAR.id(), OMAR.name(), MAYA, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
            assertThat(claim.getAssignedOfficerName()).isEqualTo("Omar");
            assertThat(claim.getHistory().get(claim.getHistory().size() - 1).getComment())
                    .isEqualTo("Reassigned from Olivia to Omar");
            assertThat(claim.pullEvents()).singleElement()
                    .satisfies(e -> assertThat(e.data()).containsEntry("reassigned", true));
        }

        @Test
        void closedClaimCannotBeAssigned() {
            claim.withdraw("changed my mind", ALICE, NOW);
            assertThatThrownBy(() -> claim.assign(OLIVIA.id(), OLIVIA.name(), MAYA, NOW))
                    .extracting("kind").isEqualTo(Kind.INVALID_TRANSITION);
        }
    }

    @Nested
    class InformationRequests {

        @BeforeEach
        void pickUp() {
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            claim.pullEvents();
        }

        @Test
        void claimReturnsToReviewOnlyWhenAllRequestsAreAnswered() {
            InfoRequest first = claim.requestInformation("Police report number?", OLIVIA, NOW);
            InfoRequest second = claim.requestInformation("Photos of the damage?", OLIVIA, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INFO_REQUESTED);
            assertThat(claim.openInfoRequests()).hasSize(2);

            claim.respondToInformationRequest(first.getId(), "PR-12345", ALICE, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INFO_REQUESTED);

            claim.respondToInformationRequest(second.getId(), "Uploaded to email", ALICE, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
            assertThat(eventTypes(claim)).containsExactly(ClaimDomainEvent.INFO_REQUESTED,
                    ClaimDomainEvent.INFO_REQUESTED, ClaimDomainEvent.INFO_PROVIDED, ClaimDomainEvent.INFO_PROVIDED);
        }

        @Test
        void onlyTheOwnerCanAnswer() {
            InfoRequest request = claim.requestInformation("Police report number?", OLIVIA, NOW);
            assertThatThrownBy(() -> claim.respondToInformationRequest(request.getId(), "x", BOB, NOW))
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);
        }

        @Test
        void aRequestCannotBeAnsweredTwice() {
            InfoRequest a = claim.requestInformation("Q1?", OLIVIA, NOW);
            claim.requestInformation("Q2?", OLIVIA, NOW);
            claim.respondToInformationRequest(a.getId(), "answer", ALICE, NOW);
            assertThatThrownBy(() -> claim.respondToInformationRequest(a.getId(), "again", ALICE, NOW))
                    .hasMessageContaining("already been answered");
        }

        @Test
        void onlyTheAssignedOfficerOrAManagerCanAsk() {
            assertThatThrownBy(() -> claim.requestInformation("Q?", OMAR, NOW))
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);
            claim.requestInformation("Q?", MAYA, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INFO_REQUESTED);
        }
    }

    @Nested
    class DecisionPath {

        @BeforeEach
        void pickUp() {
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
        }

        @Test
        void fullHappyPathToSettlement() {
            claim.assess(Liability.ACCEPTED, new BigDecimal("7200"), new BigDecimal("7200"), "All verified", OLIVIA,
                    NOW);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("7200");
            claim.approve(new BigDecimal("7200"), AUTHORITY, OLIVIA, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
            claim.settle("PAY-1", OLIVIA, NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
            assertThat(claim.getPaidAmount()).isEqualByComparingTo("7200");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThat(claim.getClosedAt()).isEqualTo(NOW);
            assertThat(eventTypes(claim)).containsExactly(ClaimDomainEvent.SUBMITTED, ClaimDomainEvent.ASSIGNED,
                    ClaimDomainEvent.ASSESSED, ClaimDomainEvent.APPROVED, ClaimDomainEvent.SETTLED);
            assertThat(claim.getHistory()).extracting(ClaimStatusHistory::getToStatus).containsExactly(
                    ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.ASSESSED, ClaimStatus.APPROVED,
                    ClaimStatus.SETTLED);
        }

        @Test
        void settledEventCarriesAmountAndReference() {
            claim.assess(Liability.ACCEPTED, new BigDecimal("7200"), new BigDecimal("7200"), "ok", OLIVIA, NOW);
            claim.approve(new BigDecimal("7000"), AUTHORITY, OLIVIA, NOW);
            claim.pullEvents();
            claim.settle("PAY-2", OLIVIA, NOW);
            Map<String, Object> data = claim.pullEvents().get(0).data();
            assertThat(data).containsEntry("paymentReference", "PAY-2");
            assertThat((BigDecimal) data.get("amount")).isEqualByComparingTo("7000");
        }

        @Test
        void cannotApproveBeforeAssessment() {
            assertThatThrownBy(() -> claim.approve(new BigDecimal("100"), AUTHORITY, OLIVIA, NOW))
                    .extracting("kind").isEqualTo(Kind.INVALID_TRANSITION);
        }

        @Test
        void cannotSettleBeforeApproval() {
            claim.assess(Liability.ACCEPTED, new BigDecimal("100"), new BigDecimal("100"), "ok", OLIVIA, NOW);
            assertThatThrownBy(() -> claim.settle("PAY", OLIVIA, NOW))
                    .extracting("kind").isEqualTo(Kind.INVALID_TRANSITION);
        }

        @Test
        void deniedLiabilityMustRecommendZeroAndCannotBeApproved() {
            assertThatThrownBy(() -> claim.assess(Liability.DENIED, new BigDecimal("100"), BigDecimal.ZERO, "no",
                    OLIVIA, NOW)).extracting("kind").isEqualTo(Kind.RULE_VIOLATION);
            claim.assess(Liability.DENIED, BigDecimal.ZERO, BigDecimal.ZERO, "Excluded peril", OLIVIA, NOW);
            assertThatThrownBy(() -> claim.approve(new BigDecimal("100"), AUTHORITY, OLIVIA, NOW))
                    .hasMessageContaining("denies liability");
        }

        @Test
        void amountsCannotExceedSumInsuredLessDeductible() {
            assertThatThrownBy(() -> claim.assess(Liability.ACCEPTED, new BigDecimal("59600"),
                    new BigDecimal("100"), "too much", OLIVIA, NOW))
                    .hasMessageContaining("maximum payable");
        }

        @Test
        void officerAuthorityLimitRequiresManagerAboveUsd25k() {
            Claim big = submit(new BigDecimal("50000"));
            big.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            big.assess(Liability.ACCEPTED, new BigDecimal("40000"), new BigDecimal("40000"), "ok", OLIVIA, NOW);

            assertThatThrownBy(() -> big.approve(new BigDecimal("40000"), AUTHORITY, OLIVIA, NOW))
                    .isInstanceOf(ClaimRuleException.class)
                    .hasMessageContaining("authority limit")
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);

            big.approve(new BigDecimal("40000"), AUTHORITY, MAYA, NOW);
            assertThat(big.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        }

        @Test
        void reassessmentUpdatesReserve() {
            claim.assess(Liability.ACCEPTED, new BigDecimal("7000"), new BigDecimal("7000"), "first", OLIVIA, NOW);
            claim.assess(Liability.PARTIAL, new BigDecimal("5000"), new BigDecimal("5000"), "second", OLIVIA,
                    NOW.plusSeconds(60));
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("5000");
            assertThat(claim.getAssessments()).hasSize(2);
            assertThat(claim.latestAssessment()).get().extracting(Assessment::getFindings).isEqualTo("second");
        }

        @Test
        void approvalIsCappedByWhatOtherClaimsOnThePolicyAlreadyUsed() {
            // sum insured SGD 60,000; another claim on the same policy is already approved for SGD 55,000
            claim.assess(Liability.ACCEPTED, new BigDecimal("7200"), new BigDecimal("7200"), "ok", OLIVIA, NOW);
            assertThatThrownBy(() -> claim.approve(new BigDecimal("7200"), new BigDecimal("55000"), AUTHORITY,
                    OLIVIA, NOW))
                    .hasMessageContaining("only SGD 5000 of the SGD 60000 sum insured is left")
                    .extracting("kind").isEqualTo(Kind.RULE_VIOLATION);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ASSESSED);

            claim.approve(new BigDecimal("5000"), new BigDecimal("55000"), AUTHORITY, OLIVIA, NOW);
            assertThat(claim.getApprovedAmount()).isEqualByComparingTo("5000");
        }

        @Test
        void rejectionClosesClaimAndReleasesReserve() {
            claim.reject(RejectionReason.POLICY_EXCLUSION, "Wear and tear is excluded under section 4.", OLIVIA,
                    NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThat(claim.getRejectionReason()).isEqualTo(RejectionReason.POLICY_EXCLUSION);
            assertThatThrownBy(() -> claim.assess(Liability.ACCEPTED, BigDecimal.ONE, BigDecimal.ONE, "x", OLIVIA,
                    NOW)).extracting("kind").isEqualTo(Kind.INVALID_TRANSITION);
        }
    }

    @Nested
    class Withdrawal {

        @Test
        void ownerCanWithdrawSubmittedClaim() {
            claim.withdraw("Repaired privately", ALICE, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.WITHDRAWN);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        }

        @Test
        void withdrawingCancelsOpenInformationRequests() {
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            claim.requestInformation("Q?", OLIVIA, NOW);
            claim.withdraw(null, ALICE, NOW);
            assertThat(claim.openInfoRequests()).isEmpty();
            assertThat(claim.getInfoRequests()).extracting(InfoRequest::getStatus)
                    .containsExactly(InfoRequest.Status.CANCELLED);
        }

        @Test
        void cannotWithdrawWhileUnderReview() {
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            assertThatThrownBy(() -> claim.withdraw(null, ALICE, NOW))
                    .extracting("kind").isEqualTo(Kind.INVALID_TRANSITION);
        }

        @Test
        void otherClaimantCannotWithdraw() {
            assertThatThrownBy(() -> claim.withdraw(null, BOB, NOW))
                    .extracting("kind").isEqualTo(Kind.NOT_PERMITTED);
        }
    }

    @Test
    void staleVersionIsRejected() {
        claim.checkVersion(null);
        claim.checkVersion(0L);
        assertThatThrownBy(() -> claim.checkVersion(3L)).extracting("kind").isEqualTo(Kind.STALE_VERSION);
    }

    @Test
    void overdueOnlyWhileOpenAndPastSla() {
        assertThat(claim.isOverdue(NOW)).isFalse();
        assertThat(claim.isOverdue(NOW.plus(Priority.LOW.sla()).plusSeconds(1))).isTrue();
        claim.withdraw(null, ALICE, NOW);
        assertThat(claim.isOverdue(NOW.plus(Priority.LOW.sla()).plusSeconds(1))).isFalse();
    }
}
