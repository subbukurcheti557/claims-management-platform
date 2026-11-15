package com.icmp.claims.claim.domain;

public enum RejectionReason {
    NOT_COVERED("The loss is not covered by the policy."),
    POLICY_EXCLUSION("The loss falls under a policy exclusion."),
    INSUFFICIENT_EVIDENCE("There was not enough evidence to support the claim."),
    LATE_NOTIFICATION("The claim was reported outside the notification period."),
    FRAUD_SUSPECTED("The claim could not be verified."),
    OTHER("See the explanation from your claims officer.");

    private final String description;

    RejectionReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
