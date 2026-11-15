package com.icmp.identity.user.domain;

public enum Role {
    CLAIMANT,
    CLAIMS_OFFICER,
    CLAIMS_MANAGER;

    public boolean isStaff() {
        return this == CLAIMS_OFFICER || this == CLAIMS_MANAGER;
    }
}
