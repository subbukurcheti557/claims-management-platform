package com.icmp.claims.claim.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.icmp.claims.claim.application.port.PolicyCoveragePort.Coverage;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.common.error.ApiException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

class PolicyCoverageRestAdapterTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 10);

    MockRestServiceServer server;
    PolicyCoverageRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(breakers,
                TimeLimiterRegistry.ofDefaults(), null, new Resilience4JConfigurationProperties());
        adapter = new PolicyCoverageRestAdapter(builder, factory, "http://policy-service");
    }

    private String url() {
        return "http://policy-service/internal/policies/SG-MOT-1001/coverage?claimantId=" + ALICE
                + "&incidentDate=2026-11-10&claimType=MOTOR";
    }

    @Test
    void mapsCoverageAndForwardsTheCallersToken() {
        server.expect(requestTo(url())).andExpect(header("Authorization", "Bearer tkn"))
                .andRespond(withSuccess("""
                        {"policyNumber":"SG-MOT-1001","covered":true,"reasons":[],"product":"MOTOR","market":"SG",
                         "currency":"SGD","sumInsured":60000,"deductible":500,"remainingSumInsured":56800,
                         "holderName":"Alice Tan","insuredItem":"Toyota"}""", MediaType.APPLICATION_JSON));

        Optional<Coverage> coverage = adapter.checkCoverage("SG-MOT-1001", ALICE, DATE, ClaimType.MOTOR, "tkn");

        assertThat(coverage).get().satisfies(c -> {
            assertThat(c.covered()).isTrue();
            assertThat(c.deductible()).isEqualByComparingTo("500");
            assertThat(c.remainingSumInsured()).isEqualByComparingTo("56800");
        });
        server.verify();
    }

    @Test
    void notFoundIsAnAnswerNotAFailure() {
        server.expect(ExpectedCount.times(6), requestTo(url())).andRespond(withResourceNotFound());
        for (int i = 0; i < 6; i++) {
            assertThat(adapter.checkCoverage("SG-MOT-1001", ALICE, DATE, ClaimType.MOTOR, "tkn")).isEmpty();
        }
        // every call reached policy-service: 404s never opened the breaker
        server.verify();
    }

    @Test
    void serverErrorsBecome503AndOpenTheCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(url())).andRespond(withServerError());

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> adapter.checkCoverage("SG-MOT-1001", ALICE, DATE, ClaimType.MOTOR, "tkn"))
                    .isInstanceOf(ApiException.class)
                    .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        // circuit is now open: the call fails fast without reaching policy-service
        assertThatThrownBy(() -> adapter.checkCoverage("SG-MOT-1001", ALICE, DATE, ClaimType.MOTOR, "tkn"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }
}
