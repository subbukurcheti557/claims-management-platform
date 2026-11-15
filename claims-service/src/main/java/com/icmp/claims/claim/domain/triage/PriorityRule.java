package com.icmp.claims.claim.domain.triage;

import java.util.Optional;

import com.icmp.claims.claim.domain.Priority;

/** One triage rule (Strategy). A new rule is a new implementation; existing rules do not change. */
public interface PriorityRule {

    Optional<Priority> evaluate(TriageInput input);
}
