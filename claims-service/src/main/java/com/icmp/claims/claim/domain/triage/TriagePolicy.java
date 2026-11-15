package com.icmp.claims.claim.domain.triage;

import java.util.List;

import com.icmp.claims.claim.domain.Priority;

/** Runs every rule and keeps the highest priority any of them returns; LOW when none applies. */
public class TriagePolicy {

    private final List<PriorityRule> rules;

    public TriagePolicy(List<PriorityRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Priority prioritise(TriageInput input) {
        Priority result = Priority.LOW;
        for (PriorityRule rule : rules) {
            result = rule.evaluate(input).map(result::max).orElse(result);
        }
        return result;
    }
}
