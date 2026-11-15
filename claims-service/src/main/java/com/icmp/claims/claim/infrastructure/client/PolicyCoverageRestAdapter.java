package com.icmp.claims.claim.infrastructure.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.icmp.claims.claim.application.port.PolicyCoveragePort;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.common.error.ApiException;
import com.icmp.claims.common.web.CorrelationIdFilter;

/**
 * Adapter: synchronous coverage check against policy-service, protected by a circuit breaker and time limiter.
 * A missing policy (404) is a normal answer, not a failure, so it does not trip the breaker.
 */
@Component
public class PolicyCoverageRestAdapter implements PolicyCoveragePort {

    private static final Logger log = LoggerFactory.getLogger(PolicyCoverageRestAdapter.class);

    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public PolicyCoverageRestAdapter(RestClient.Builder loadBalancedRestClientBuilder,
                                     CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                                     @Value("${app.services.policy-url:http://policy-service}") String baseUrl) {
        this.client = loadBalancedRestClientBuilder.clone().baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerFactory.create("policy-service");
    }

    @Override
    public Optional<Coverage> checkCoverage(String policyNumber, UUID claimantId, LocalDate incidentDate,
                                            ClaimType claimType, String bearerToken) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return circuitBreaker.run(() -> call(policyNumber, claimantId, incidentDate, claimType, bearerToken,
                correlationId), failure -> {
                    if (failure instanceof ApiException api) {
                        throw api;
                    }
                    log.warn("Coverage check for {} failed: {}", policyNumber, failure.toString());
                    throw ApiException.unavailable("We could not verify your policy right now. Please try again "
                            + "in a few minutes.");
                });
    }

    private Optional<Coverage> call(String policyNumber, UUID claimantId, LocalDate incidentDate,
                                    ClaimType claimType, String bearerToken, String correlationId) {
        try {
            CoverageResponse response = client.get()
                    .uri(uri -> uri.path("/internal/policies/{policyNumber}/coverage")
                            .queryParam("claimantId", claimantId)
                            .queryParam("incidentDate", incidentDate)
                            .queryParam("claimType", claimType)
                            .build(policyNumber))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .header(CorrelationIdFilter.HEADER, correlationId == null ? "" : correlationId)
                    .retrieve()
                    .body(CoverageResponse.class);
            if (response == null) {
                throw new IllegalStateException("Empty coverage response");
            }
            return Optional.of(new Coverage(response.policyNumber(), response.covered(),
                    response.reasons() == null ? List.of() : response.reasons(), response.market(),
                    response.currency(), response.sumInsured(), response.deductible(),
                    response.remainingSumInsured(), response.insuredItem()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden e) {
            throw ApiException.forbidden("You can only report claims against your own policies.");
        }
    }

    record CoverageResponse(String policyNumber, boolean covered, List<String> reasons, String product,
                            String market, String currency, BigDecimal sumInsured, BigDecimal deductible,
                            BigDecimal remainingSumInsured, String holderName, String insuredItem) {
    }
}
