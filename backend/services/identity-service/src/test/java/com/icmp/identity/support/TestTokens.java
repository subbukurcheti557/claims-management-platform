package com.icmp.identity.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Mints tokens exactly like identity-service does, for the seeded demo users. */
public final class TestTokens {

    public static final String SECRET = "local-dev-secret-change-me-at-least-32-bytes-long!!";

    public static final String ALICE = "11111111-1111-1111-1111-111111111111";
    public static final String BOB = "22222222-2222-2222-2222-222222222222";
    public static final String OLIVIA = "33333333-3333-3333-3333-333333333333";
    public static final String OMAR = "44444444-4444-4444-4444-444444444444";
    public static final String MAYA = "55555555-5555-5555-5555-555555555555";

    private TestTokens() {
    }

    public static String alice() {
        return token(ALICE, "alice@claimant.test", "Alice Tan", "CLAIMANT", "SG");
    }

    public static String bob() {
        return token(BOB, "bob@claimant.test", "Bob Wong", "CLAIMANT", "HK");
    }

    public static String olivia() {
        return token(OLIVIA, "olivia@staff.test", "Olivia Lim", "CLAIMS_OFFICER", "SG");
    }

    public static String omar() {
        return token(OMAR, "omar@staff.test", "Omar Rahman", "CLAIMS_OFFICER", "MY");
    }

    public static String maya() {
        return token(MAYA, "maya@staff.test", "Maya Chen", "CLAIMS_MANAGER", "SG");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String token(String userId, String email, String name, String role, String market) {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("icmp-identity").subject(userId).issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("email", email).claim("name", name)
                .claim("role", role).claim("market", market).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
