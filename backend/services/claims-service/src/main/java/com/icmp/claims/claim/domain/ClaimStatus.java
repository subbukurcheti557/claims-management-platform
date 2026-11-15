package com.icmp.claims.claim.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Claim lifecycle states and the only transitions allowed between them (State pattern as a transition table).
 * Every status change on {@link Claim} goes through {@link #canTransitionTo(ClaimStatus)}.
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    ASSESSED,
    APPROVED,
    SETTLED,
    REJECTED,
    WITHDRAWN;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(INFO_REQUESTED, ASSESSED, REJECTED),
            INFO_REQUESTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            ASSESSED, EnumSet.of(ASSESSED, INFO_REQUESTED, APPROVED, REJECTED),
            APPROVED, EnumSet.of(SETTLED),
            SETTLED, EnumSet.noneOf(ClaimStatus.class),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            WITHDRAWN, EnumSet.noneOf(ClaimStatus.class));

    /** Statuses that still carry outstanding liability. */
    public static final Set<ClaimStatus> OPEN = EnumSet.of(SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, ASSESSED, APPROVED);

    public boolean canTransitionTo(ClaimStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<ClaimStatus> allowedTargets() {
        return EnumSet.copyOf(ALLOWED.get(this).isEmpty() ? EnumSet.noneOf(ClaimStatus.class) : ALLOWED.get(this));
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isClosed() {
        return !isOpen();
    }
}
