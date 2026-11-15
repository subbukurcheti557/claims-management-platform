package com.icmp.identity.auth.application;

import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.icmp.identity.user.domain.AppUser;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Issues signed HS256 access tokens. */
@Service
public class TokenService {

    public static final String ISSUER = "icmp-identity";

    private final JwtEncoder encoder;
    private final Duration ttl;

    public TokenService(SecretKey jwtSecretKey, @Value("${security.jwt.ttl:PT60M}") Duration ttl) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
        this.ttl = ttl;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .claim("market", user.getMarket())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
