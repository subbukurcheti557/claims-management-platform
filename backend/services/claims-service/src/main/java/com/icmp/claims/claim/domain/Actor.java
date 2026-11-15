package com.icmp.claims.claim.domain;

import java.util.Objects;
import java.util.UUID;

/** Who is performing an action on a claim. Kept in the domain so the aggregate can enforce role rules. */
public record Actor(UUID id, String name, String role) {

    public static final String CLAIMANT = "CLAIMANT";
    public static final String CLAIMS_OFFICER = "CLAIMS_OFFICER";
    public static final String CLAIMS_MANAGER = "CLAIMS_MANAGER";

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public boolean isManager() {
        return CLAIMS_MANAGER.equals(role);
    }

    public boolean isStaff() {
        return CLAIMS_OFFICER.equals(role) || CLAIMS_MANAGER.equals(role);
    }

    public boolean isClaimant() {
        return CLAIMANT.equals(role);
    }
}
