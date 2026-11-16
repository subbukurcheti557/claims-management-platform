package com.icmp.claims.claim.domain;

import static com.icmp.claims.claim.domain.ClaimTest.AUTHORITY;
import static com.icmp.claims.claim.domain.ClaimTest.ALICE;
import static com.icmp.claims.claim.domain.ClaimTest.NOW;
import static com.icmp.claims.claim.domain.ClaimTest.OLIVIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.icmp.claims.claim.domain.ClaimRuleException.Kind;

class DuplicateClaimPolicyTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    static Claim paid() {
        Claim c = ClaimTest.submit(new BigDecimal("8000"));
        c.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
        c.assess(Liability.ACCEPTED, new BigDecimal("7200"), new BigDecimal("7200"), "ok", OLIVIA, NOW);
        c.approve(new BigDecimal("7200"), AUTHORITY, OLIVIA, NOW);
        c.settle("PAY-1", OLIVIA, NOW);
        return c;
    }

    static Claim withdrawn() {
        Claim c = ClaimTest.submit(new BigDecimal("8000"));
        c.withdraw("Reported by mistake", ALICE, NOW);
        return c;
    }

    @Test
    void noEarlierClaimsMeansNoFlag() {
        assertThat(DuplicateClaimPolicy.check("SG-MOT-1001", DAY, List.of())).isEmpty();
    }

    @Test
    void anOpenClaimForTheSameIncidentIsRefusedWith409Kind() {
        Claim open = ClaimTest.submit(new BigDecimal("8000"));
        assertThatThrownBy(() -> DuplicateClaimPolicy.check("SG-MOT-1001", DAY, List.of(withdrawn(), open)))
                .isInstanceOf(ClaimRuleException.class)
                .hasMessageContaining("Claim CLM-SG-2026-000100 for policy SG-MOT-1001 and incident date 2026-11-10")
                .hasMessageContaining("already being handled (status SUBMITTED)")
                .extracting("kind").isEqualTo(Kind.DUPLICATE_CLAIM);
    }

    @Test
    void anApprovedButUnpaidClaimStillCountsAsOpen() {
        Claim approved = ClaimTest.submit(new BigDecimal("8000"));
        approved.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
        approved.assess(Liability.ACCEPTED, new BigDecimal("7200"), new BigDecimal("7200"), "ok", OLIVIA, NOW);
        approved.approve(new BigDecimal("7200"), AUTHORITY, OLIVIA, NOW);
        assertThatThrownBy(() -> DuplicateClaimPolicy.check("SG-MOT-1001", DAY, List.of(approved)))
                .extracting("kind").isEqualTo(Kind.DUPLICATE_CLAIM);
    }

    @Test
    void aPaidClaimForTheSameIncidentFlagsTheNewOne() {
        assertThat(DuplicateClaimPolicy.check("SG-MOT-1001", DAY, List.of(paid())))
                .contains("CLM-SG-2026-000100");
    }

    @Test
    void rejectedAndWithdrawnClaimsAreIgnored() {
        Claim rejected = ClaimTest.submit(new BigDecimal("8000"));
        rejected.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
        rejected.reject(RejectionReason.values()[0], "Not covered", OLIVIA, NOW);
        assertThat(DuplicateClaimPolicy.check("SG-MOT-1001", DAY, List.of(rejected, withdrawn()))).isEmpty();
    }
}
