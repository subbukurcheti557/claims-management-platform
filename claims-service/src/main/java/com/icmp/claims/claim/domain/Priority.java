package com.icmp.claims.claim.domain;

import java.time.Duration;

/** Triage priority and the target number of days to resolve (the claim's SLA). */
public enum Priority {
    LOW(21),
    MEDIUM(14),
    HIGH(7);

    private final int slaDays;

    Priority(int slaDays) {
        this.slaDays = slaDays;
    }

    public Duration sla() {
        return Duration.ofDays(slaDays);
    }

    public int slaDays() {
        return slaDays;
    }

    public Priority max(Priority other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
