package com.icmp.claims.claim.domain.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Priority;

class TriagePolicyTest {

    private final TriagePolicy policy = new TriagePolicy(List.of(
            new HighValueRule(new BigDecimal("50000"), new BigDecimal("15000")),
            new InjuryRule(),
            new CatastropheKeywordRule()));

    private Priority triage(ClaimType type, String usd, boolean injury, String description) {
        return policy.prioritise(new TriageInput(type, new BigDecimal(usd), injury, description));
    }

    @Test
    void smallMotorClaimIsLow() {
        assertThat(triage(ClaimType.MOTOR, "3000", false, "scratched door")).isEqualTo(Priority.LOW);
    }

    @Test
    void midValueIsMediumAndHighValueIsHigh() {
        assertThat(triage(ClaimType.MOTOR, "20000", false, "front end damage")).isEqualTo(Priority.MEDIUM);
        assertThat(triage(ClaimType.MOTOR, "60000", false, "write-off")).isEqualTo(Priority.HIGH);
    }

    @Test
    void injuryAlwaysWins() {
        assertThat(triage(ClaimType.MOTOR, "500", true, "minor bump")).isEqualTo(Priority.HIGH);
    }

    @Test
    void catastrophePerilRaisesPropertyClaimsOnly() {
        assertThat(triage(ClaimType.PROPERTY, "2000", false, "Typhoon broke the windows")).isEqualTo(Priority.MEDIUM);
        assertThat(triage(ClaimType.MOTOR, "2000", false, "Car damaged in a storm")).isEqualTo(Priority.LOW);
    }

    @Test
    void highestPriorityAcrossRulesIsKept() {
        assertThat(triage(ClaimType.PROPERTY, "80000", false, "flood")).isEqualTo(Priority.HIGH);
    }
}
