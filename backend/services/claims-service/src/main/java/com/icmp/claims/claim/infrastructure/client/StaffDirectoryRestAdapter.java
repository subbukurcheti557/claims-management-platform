package com.icmp.claims.claim.infrastructure.client;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.icmp.claims.claim.application.port.StaffDirectoryPort;
import com.icmp.claims.common.error.ApiException;

/** Adapter: validates assignees against identity-service when a manager assigns a claim to someone else. */
@Component
public class StaffDirectoryRestAdapter implements StaffDirectoryPort {

    private static final Logger log = LoggerFactory.getLogger(StaffDirectoryRestAdapter.class);

    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public StaffDirectoryRestAdapter(RestClient.Builder loadBalancedRestClientBuilder,
                                     CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                                     @Value("${app.services.identity-url:http://identity-service}") String baseUrl) {
        this.client = loadBalancedRestClientBuilder.clone().baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerFactory.create("identity-service");
    }

    @Override
    public Optional<StaffMember> findStaff(UUID userId, String bearerToken) {
        return circuitBreaker.run(() -> {
            try {
                UserResponse user = client.get().uri("/internal/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .retrieve().body(UserResponse.class);
                return Optional.ofNullable(user).map(u -> new StaffMember(u.id(), u.fullName(), u.role()));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.<StaffMember>empty();
            }
        }, failure -> {
            log.warn("Staff lookup for {} failed: {}", userId, failure.toString());
            throw ApiException.unavailable("The staff directory is not available right now. Try again shortly.");
        });
    }

    record UserResponse(UUID id, String email, String fullName, String role, String market) {
    }
}
