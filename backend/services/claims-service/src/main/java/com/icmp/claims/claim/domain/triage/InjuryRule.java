package com.icmp.claims.claim.domain.triage;

import java.util.Optional;

import com.icmp.claims.claim.domain.Priority;

/** Any reported injury makes the claim HIGH priority. */
public class InjuryRule implements PriorityRule {

    @Override
    public Optional<Priority> evaluate(TriageInput input) {
        return input.injuryReported() ? Optional.of(Priority.HIGH) : Optional.empty();
    }
}
