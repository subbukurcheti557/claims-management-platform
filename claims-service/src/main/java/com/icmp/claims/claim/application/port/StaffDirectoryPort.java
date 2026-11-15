package com.icmp.claims.claim.application.port;

import java.util.Optional;
import java.util.UUID;

/** Looks up staff users, so a manager can only assign claims to real, active claims staff. */
public interface StaffDirectoryPort {

    Optional<StaffMember> findStaff(UUID userId, String bearerToken);

    record StaffMember(UUID id, String fullName, String role) {
        public boolean isClaimsStaff() {
            return "CLAIMS_OFFICER".equals(role) || "CLAIMS_MANAGER".equals(role);
        }
    }
}
