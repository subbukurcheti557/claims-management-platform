package com.icmp.claims.claim.domain;

import static com.icmp.claims.claim.domain.ClaimStatus.APPROVED;
import static com.icmp.claims.claim.domain.ClaimStatus.ASSESSED;
import static com.icmp.claims.claim.domain.ClaimStatus.INFO_REQUESTED;
import static com.icmp.claims.claim.domain.ClaimStatus.REJECTED;
import static com.icmp.claims.claim.domain.ClaimStatus.SETTLED;
import static com.icmp.claims.claim.domain.ClaimStatus.SUBMITTED;
import static com.icmp.claims.claim.domain.ClaimStatus.UNDER_REVIEW;
import static com.icmp.claims.claim.domain.ClaimStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClaimStatusTest {

    private static final Map<ClaimStatus, Set<ClaimStatus>> EXPECTED = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(INFO_REQUESTED, ASSESSED, REJECTED),
            INFO_REQUESTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            ASSESSED, EnumSet.of(ASSESSED, INFO_REQUESTED, APPROVED, REJECTED),
            APPROVED, EnumSet.of(SETTLED),
            SETTLED, EnumSet.noneOf(ClaimStatus.class),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            WITHDRAWN, EnumSet.noneOf(ClaimStatus.class));

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    void everyPairOfStatusesFollowsTheTransitionTable(ClaimStatus from) {
        for (ClaimStatus to : ClaimStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"SETTLED", "REJECTED", "WITHDRAWN"})
    void closedStatusesAreTerminalAndCarryNoLiability(ClaimStatus status) {
        assertThat(status.isClosed()).isTrue();
        assertThat(status.allowedTargets()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"SUBMITTED", "UNDER_REVIEW", "INFO_REQUESTED", "ASSESSED", "APPROVED"})
    void openStatusesCountTowardsExposure(ClaimStatus status) {
        assertThat(status.isOpen()).isTrue();
    }
}
