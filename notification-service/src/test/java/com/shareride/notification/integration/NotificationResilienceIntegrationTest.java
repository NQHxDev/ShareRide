package com.shareride.notification.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.shareride.notification.client.ResendEmailClient;
import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.config.ResendProperties;
import com.shareride.notification.support.AbstractIntegrationTest;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Integration test for notification resilience patterns: HTTP 429 retry with exponential backoff
 * and permanent failure handling for DLQ routing.
 *
 * <p>Uses HTTP/1.1 explicit configuration for WireMock compatibility.
 */
class NotificationResilienceIntegrationTest extends AbstractIntegrationTest {

    private static final WireMockServer wireMockServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureResendProperties(DynamicPropertyRegistry registry) {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        registry.add("shareride.resend.api-url", wireMockServer::baseUrl);
        registry.add("shareride.resend.api-key", () -> "test-api-key");
        registry.add("shareride.resend.from-email", () -> "noreply@shareride.vn");
    }

    @TestConfiguration
    static class WireMockRestClientConfig {
        @Bean
        @Primary
        public RestClient wireMockRestClient(ResendProperties properties) {
            HttpClient httpClient =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            return RestClient.builder()
                    .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                    .baseUrl(properties.getApiUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
    }

    @Autowired private ResendEmailClient resendClient;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
    }

    @Test
    void shouldRetryOnHttp429RateLimitError() {
        wireMockServer.stubFor(
                post(urlEqualTo("/emails"))
                        .withHeader("Authorization", equalTo("Bearer test-api-key"))
                        .willReturn(
                                aResponse()
                                        .withStatus(429)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"error\":\"Rate limit exceeded. Please retry"
                                                        + " after 1 second.\"}")));

        ResendEmailRequest request =
                ResendEmailRequest.builder()
                        .from("ShareRide <noreply@shareride.vn>")
                        .to(List.of("user@example.com"))
                        .subject("Test Email")
                        .html("<p>Test content</p>")
                        .text("Test content")
                        .build();

        assertThatThrownBy(() -> resendClient.sendEmail(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("permanently failed");

        wireMockServer.verify(
                3,
                postRequestedFor(urlEqualTo("/emails"))
                        .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }

    @Test
    void shouldRetryOnHttp5xxServerError() {
        wireMockServer.stubFor(
                post(urlEqualTo("/emails"))
                        .withHeader("Authorization", equalTo("Bearer test-api-key"))
                        .willReturn(
                                aResponse()
                                        .withStatus(503)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"error\":\"Service temporarily"
                                                        + " unavailable\"}")));

        ResendEmailRequest request =
                ResendEmailRequest.builder()
                        .from("ShareRide <noreply@shareride.vn>")
                        .to(List.of("user@example.com"))
                        .subject("Test Email")
                        .html("<p>Test content</p>")
                        .build();

        assertThatThrownBy(() -> resendClient.sendEmail(request))
                .isInstanceOf(RuntimeException.class);

        wireMockServer.verify(
                3,
                postRequestedFor(urlEqualTo("/emails"))
                        .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }

    @Test
    void shouldNotRetryOnHttp400BadRequest() {
        wireMockServer.stubFor(
                post(urlEqualTo("/emails"))
                        .withHeader("Authorization", equalTo("Bearer test-api-key"))
                        .willReturn(
                                aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"error\":\"Invalid email address\"}")));

        ResendEmailRequest request =
                ResendEmailRequest.builder()
                        .from("ShareRide <noreply@shareride.vn>")
                        .to(List.of("invalid-email"))
                        .subject("Test Email")
                        .html("<p>Test content</p>")
                        .build();

        assertThatThrownBy(() -> resendClient.sendEmail(request))
                .isInstanceOf(RuntimeException.class);

        wireMockServer.verify(
                1,
                postRequestedFor(urlEqualTo("/emails"))
                        .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }
}
