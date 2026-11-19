package com.shareride.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

@DisplayName("DocsGatewayController Unit Tests")
class DocsGatewayControllerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        DocsGatewayController controller = new DocsGatewayController();
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("Should redirect root path / to /swagger-ui/index.html with 302 Found")
    void shouldRedirectRootToSwaggerUi() {
        webTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .location("/swagger-ui/index.html");
    }

    @Test
    @DisplayName("Should redirect /docs to /swagger-ui/index.html with 302 Found")
    void shouldRedirectDocsToSwaggerUi() {
        webTestClient
                .get()
                .uri("/docs")
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .location("/swagger-ui/index.html");
    }

    @Test
    @DisplayName("Should redirect /docs/ to /swagger-ui/index.html with 302 Found")
    void shouldRedirectDocsWithTrailingSlashToSwaggerUi() {
        webTestClient
                .get()
                .uri("/docs/")
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .location("/swagger-ui/index.html");
    }
}
