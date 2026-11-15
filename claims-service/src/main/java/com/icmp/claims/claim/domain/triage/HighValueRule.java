package com.icmp.claims.claim.domain.triage;

import java.math.BigDecimal;
import java.util.Optional;

import com.icmp.claims.claim.domain.Priority;

/** Large losses are HIGH; mid-size losses are at least MEDIUM. */
public class HighValueRule implements PriorityRule {

    private final BigDecimal highThresholdUsd;
    private final BigDecimal mediumThresholdUsd;

    public HighValueRule(BigDecimal highThresholdUsd, BigDecimal mediumThresholdUsd) {
        this.highThresholdUsd = highThresholdUsd;
        this.mediumThresholdUsd = mediumThresholdUsd;
    }

    @Override
    public Optional<Priority> evaluate(TriageInput input) {
        if (input.estimatedLossUsd().compareTo(highThresholdUsd) > 0) {
            return Optional.of(Priority.HIGH);
        }
        if (input.estimatedLossUsd().compareTo(mediumThresholdUsd) > 0) {
            return Optional.of(Priority.MEDIUM);
        }
        return Optional.empty();
    }
}
