package com.icmp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false"})
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    WebTestClient client;

    @LocalServerPort
    int port;

    @Test
    void rejectsRequestsWithoutToken() {
        client.get().uri("/api/claims/mine").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void blocksInternalRoutes() {
        client.get().uri("/internal/policies/X/coverage").exchange().expectStatus().is4xxClientError();
    }

    @Test
    void rejectsForgedToken() {
        client.get().uri("/api/claims/mine")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.invalid")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void healthIsPublicAndCorrelationIdIsEchoed() {
        client.get().uri("/actuator/health").header("X-Correlation-Id", "abc-123").exchange()
                .expectStatus().isOk();
    }

    @Test
    void corsPreflightIsAllowedForTheShell() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .options().uri("/api/claims/mine")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
    }
}
