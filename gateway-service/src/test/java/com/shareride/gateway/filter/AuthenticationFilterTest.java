package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shareride.common.security.jwt.JwtClaimsConstant;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("AuthenticationFilter Unit Tests")
class AuthenticationFilterTest {

    private GatewayMetricsService gatewayMetricsService;
    private AuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        gatewayMetricsService = mock(GatewayMetricsService.class);
        filter = new AuthenticationFilter(gatewayMetricsService);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName(
            "Should pass through public login endpoint without authentication and record bypass"
                    + " metric")
    void shouldPassThroughPublicLoginEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/api/v1/auth/login", "whitelisted");
    }

    @Test
    @DisplayName(
            "Should pass through public register endpoint without authentication and record bypass"
                    + " metric")
    void shouldPassThroughPublicRegisterEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/api/v1/auth/register", "whitelisted");
    }

    @Test
    @DisplayName(
            "Should pass through public JWKS endpoint without authentication and record bypass"
                    + " metric")
    void shouldPassThroughPublicJwksEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/jwks.json").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/api/v1/auth/jwks.json", "whitelisted");
    }

    @Test
    @DisplayName("Should pass through actuator health endpoints and record bypass metric")
    void shouldPassThroughActuatorHealthEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/actuator/health", "whitelisted");
    }

    @Test
    @DisplayName("Should pass through OpenAPI and Swagger UI endpoints without authentication")
    void shouldPassThroughOpenApiAndSwaggerEndpoints() {
        List<String> docPaths =
                List.of(
                        "/v3/api-docs",
                        "/v3/api-docs/swagger-config",
                        "/swagger-ui/index.html",
                        "/swagger-ui.html",
                        "/api/v1/auth/v3/api-docs",
                        "/api/v1/auth/v3/api-docs/swagger-config",
                        "/api/v1/templates/v3/api-docs");

        for (String docPath : docPaths) {
            MockServerHttpRequest request = MockServerHttpRequest.get(docPath).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
            verify(gatewayMetricsService).recordAuthEvent("PUBLIC_BYPASS", docPath, "whitelisted");
        }
    }

    @Test
    @DisplayName("Should record token present metric for protected endpoint with Bearer header")
    void shouldPassThroughProtectedEndpointWithBearerToken() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rides/current")
                        .header("Authorization", "Bearer mock-token-12345")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("TOKEN_PRESENT", "/api/v1/rides/current", "bearer");
    }

    @Test
    @DisplayName("Should record missing token metric for protected endpoint without Bearer header")
    void shouldRecordMissingTokenForProtectedEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/rides/current").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("MISSING_TOKEN", "/api/v1/rides/current", "no_bearer_token");
    }

    @Test
    @DisplayName("Should run after GatewayMetricsGlobalFilter (Ordered.HIGHEST_PRECEDENCE + 2)")
    void shouldRunAfterMetricsGlobalFilter() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
    }

    @Test
    @DisplayName(
            "Perimeter Validation: Should verify valid EdDSA token and forward context headers"
                    + " downstream")
    void shouldVerifyValidEdDsaTokenAndForwardContextHeaders() throws Exception {
        OctetKeyPair keyPair =
                new OctetKeyPairGenerator(Curve.Ed25519).keyID("shareride-test-key").generate();
        JwtTokenVerifier verifier =
                new JwtTokenVerifier(keyPair.toPublicJWK(), null, Duration.ofSeconds(60));

        AuthenticationFilter secureFilter =
                new AuthenticationFilter(gatewayMetricsService, null, providerOf(verifier), null);

        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                        .type(new JOSEObjectType(JwtClaimsConstant.HEADER_TYPE_AT_JWT))
                        .keyID(keyPair.getKeyID())
                        .build();

        JWTClaimsSet claimsSet =
                new JWTClaimsSet.Builder()
                        .jwtID(UUID.randomUUID().toString())
                        .subject(accountId.toString())
                        .issuer("shareride-auth")
                        .audience("shareride-platform")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(900)))
                        .claim(JwtClaimsConstant.CLAIM_EMAIL, "driver@shareride.com")
                        .claim(JwtClaimsConstant.CLAIM_ROLES, List.of("ROLE_DRIVER"))
                        .claim(JwtClaimsConstant.CLAIM_PERMISSIONS, List.of("ride:accept"))
                        .claim(JwtClaimsConstant.CLAIM_SESSION_ID, sessionId.toString())
                        .claim(JwtClaimsConstant.CLAIM_TENANT_ID, "vn-hcm")
                        .claim(JwtClaimsConstant.CLAIM_DEVICE_ID, "pixel-8")
                        .build();

        SignedJWT signedJwt = new SignedJWT(header, claimsSet);
        signedJwt.sign(new Ed25519Signer(keyPair));
        String validToken = signedJwt.serialize();

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rides/active")
                        .header("Authorization", "Bearer " + validToken)
                        .header("X-User-Roles", "ROLE_ADMIN_SPOOFED")
                        .header("X-User-Id", "spoofed-user-id")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        secureFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange capturedExchange = captor.getValue();

        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.USER_ID_HEADER))
                .isEqualTo(accountId.toString());
        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.USER_EMAIL_HEADER))
                .isEqualTo("driver@shareride.com");
        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.USER_ROLES_HEADER))
                .isEqualTo("ROLE_DRIVER");
        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.USER_PERMISSIONS_HEADER))
                .isEqualTo("ride:accept");
        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.SESSION_ID_HEADER))
                .isEqualTo(sessionId.toString());
        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.TENANT_ID_HEADER))
                .isEqualTo("vn-hcm");
        assertThat(
                        capturedExchange
                                .getRequest()
                                .getHeaders()
                                .getFirst(GatewayConstants.DEVICE_ID_HEADER))
                .isEqualTo("pixel-8");

        verify(gatewayMetricsService)
                .recordAuthEvent("TOKEN_VALIDATED", "/api/v1/rides/active", "success");
    }

    @Test
    @DisplayName("Perimeter Validation: Missing token with verifier configured must return 401")
    void shouldReturn401WhenTokenIsMissingAndVerifierConfigured() {
        JwtTokenVerifier verifier = mock(JwtTokenVerifier.class);

        AuthenticationFilter secureFilter =
                new AuthenticationFilter(gatewayMetricsService, null, providerOf(verifier), null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/rides/active").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        secureFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(gatewayMetricsService)
                .recordAuthEvent("MISSING_TOKEN", "/api/v1/rides/active", "no_bearer_token");
    }

    @Test
    @DisplayName("Perimeter Validation: Tampered or invalid token must return 401 Unauthorized")
    void shouldReturn401WhenTokenIsInvalidOrTampered() throws Exception {
        OctetKeyPair keyPair =
                new OctetKeyPairGenerator(Curve.Ed25519).keyID("shareride-test-key").generate();
        JwtTokenVerifier verifier =
                new JwtTokenVerifier(keyPair.toPublicJWK(), null, Duration.ofSeconds(60));

        AuthenticationFilter secureFilter =
                new AuthenticationFilter(gatewayMetricsService, null, providerOf(verifier), null);

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rides/active")
                        .header("Authorization", "Bearer invalid.token.payload")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        secureFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(gatewayMetricsService)
                .recordAuthEvent("TOKEN_INVALID", "/api/v1/rides/active", "verification_failed");
    }

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }
        };
    }
}
