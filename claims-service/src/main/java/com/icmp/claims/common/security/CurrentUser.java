package com.icmp.claims.common.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The authenticated caller, read from the validated JWT. */
public record CurrentUser(UUID id, String email, String name, String role, String market, String token) {

    public static final String CLAIMANT = "CLAIMANT";
    public static final String CLAIMS_OFFICER = "CLAIMS_OFFICER";
    public static final String CLAIMS_MANAGER = "CLAIMS_MANAGER";

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("No authenticated JWT user in context");
        }
        Jwt jwt = jwtAuth.getToken();
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("role"), jwt.getClaimAsString("market"),
                jwt.getTokenValue());
    }

    public boolean isStaff() {
        return CLAIMS_OFFICER.equals(role) || CLAIMS_MANAGER.equals(role);
    }

    public boolean isManager() {
        return CLAIMS_MANAGER.equals(role);
    }

    public boolean isClaimant() {
        return CLAIMANT.equals(role);
    }
}
