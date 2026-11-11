package com.shareride.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.security.jwt.JwtClaimsConstant;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserContextFilterTest {

    private UserContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserContextFilter();
        UserContextHolder.clear();
        TraceIdContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        TraceIdContext.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Should extract user headers, populate UserContext and MDC, and clear on finish")
    void testExtractUserHeadersAndMdc() throws ServletException, IOException {
        TraceIdContext.setTraceId("trace-ctx-123");
        TraceIdContext.setSpanId("span-ctx-456");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides");
        request.addHeader(UserContextFilter.USER_ID_HEADER, "usr-999");
        request.addHeader(UserContextFilter.USER_EMAIL_HEADER, "driver@shareride.com");
        request.addHeader(UserContextFilter.USER_ROLES_HEADER, "DRIVER, PASSENGER");
        request.addHeader(UserContextFilter.DEVICE_ID_HEADER, "device-android-12");
        request.addHeader("User-Agent", "ShareRideMobile/1.0");
        request.addHeader(UserContextFilter.FORWARDED_FOR_HEADER, "203.0.113.195, 70.41.3.18");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainExecuted = new AtomicBoolean(false);

        FilterChain filterChain =
                (req, res) -> {
                    filterChainExecuted.set(true);

                    // Verify UserContext
                    UserContext ctx = UserContextHolder.getContext();
                    assertNotNull(ctx);
                    assertEquals("usr-999", ctx.getUserId());
                    assertEquals("driver@shareride.com", ctx.getEmail());
                    assertEquals("203.0.113.195", ctx.getClientIp());
                    assertEquals("device-android-12", ctx.getDeviceId());
                    assertEquals("ShareRideMobile/1.0", ctx.getUserAgent());
                    assertEquals("trace-ctx-123", ctx.getTraceId());
                    assertEquals("span-ctx-456", ctx.getSpanId());
                    assertTrue(ctx.hasRole("DRIVER"));
                    assertTrue(ctx.hasRole("PASSENGER"));

                    // Verify MDC
                    assertEquals("usr-999", MDC.get(MdcConstants.USER_ID));
                    assertEquals("203.0.113.195", MDC.get(MdcConstants.CLIENT_IP));
                };

        filter.doFilter(request, response, filterChain);

        assertTrue(filterChainExecuted.get());
        // Verify teardown
        assertFalse(UserContextHolder.isAuthenticated());
        assertNull(MDC.get(MdcConstants.USER_ID));
        assertNull(MDC.get(MdcConstants.CLIENT_IP));
    }

    @Test
    @DisplayName("Should fall back to X-Real-IP then remoteAddr when X-Forwarded-For is absent")
    void testClientIpResolutionFallback() throws ServletException, IOException {
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/test");
        req1.addHeader(UserContextFilter.REAL_IP_HEADER, "198.51.100.22");
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        filter.doFilter(
                req1,
                res1,
                (req, res) -> {
                    assertEquals("198.51.100.22", UserContextHolder.getClientIp());
                });

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/test");
        req2.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        filter.doFilter(
                req2,
                res2,
                (req, res) -> {
                    assertEquals("127.0.0.1", UserContextHolder.getClientIp());
                });
    }

    @Test
    @DisplayName(
            "Should parse W3C baggage header and populate tenant, priority, and feature flags into"
                    + " UserContext and MDC")
    void testExtractBaggageAndW3cHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides");
        request.addHeader(
                "baggage",
                "tenantId=tenant-danang,userId=usr-77,priority=HIGH,correlationId=corr-xyz,featureFlags=surge_pricing");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {
                    UserContext ctx = UserContextHolder.getContext();
                    assertNotNull(ctx);
                    assertEquals("tenant-danang", ctx.getTenantId());
                    assertEquals("usr-77", ctx.getUserId());
                    assertEquals("HIGH", ctx.getPriority());
                    assertTrue(ctx.isHighPriority());
                    assertEquals("corr-xyz", ctx.getCorrelationId());
                    assertTrue(ctx.hasFeatureFlag("surge_pricing"));

                    assertEquals("tenant-danang", MDC.get(MdcConstants.TENANT_ID));
                    assertEquals("usr-77", MDC.get(MdcConstants.USER_ID));
                    assertEquals("HIGH", MDC.get(MdcConstants.PRIORITY));
                    assertEquals("corr-xyz", MDC.get(MdcConstants.CORRELATION_ID));
                });

        assertNull(MDC.get(MdcConstants.TENANT_ID));
        assertNull(MDC.get(MdcConstants.PRIORITY));
    }

    @Test
    @DisplayName(
            "Should generate distinct Correlation ID when absent, never falling back to traceId")
    void testCorrelationIdDistinctFromTraceIdWhenAbsent() throws ServletException, IOException {
        String technicalTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        TraceIdContext.setTraceId(technicalTraceId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {
                    UserContext ctx = UserContextHolder.getContext();
                    assertNotNull(ctx);
                    assertNotNull(ctx.getCorrelationId());
                    assertNotEquals(
                            technicalTraceId,
                            ctx.getCorrelationId(),
                            "Correlation ID must not equal Trace ID!");
                    assertEquals(ctx.getCorrelationId(), MDC.get(MdcConstants.CORRELATION_ID));
                });

        String responseCid = response.getHeader(UserContextFilter.CORRELATION_ID_HEADER);
        assertNotNull(responseCid);
        assertNotEquals(technicalTraceId, responseCid);
    }

    @Test
    @DisplayName("Should extract X-User-Permissions into UserContext")
    void testExtractPermissionsHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader(UserContextFilter.USER_ID_HEADER, "usr-888");
        request.addHeader(
                UserContextFilter.USER_PERMISSIONS_HEADER, "account:read, account:update");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {
                    UserContext ctx = UserContextHolder.getContext();
                    assertNotNull(ctx);
                    assertEquals("usr-888", ctx.getUserId());
                    assertTrue(ctx.hasPermission("account:read"));
                    assertTrue(ctx.hasPermission("account:update"));
                    assertFalse(ctx.hasPermission("account:delete"));
                });
    }

    @Test
    @DisplayName(
            "Microservice Defense-in-Depth: Verified Bearer token overrides unverified headers")
    void testDownstreamJwtVerification() throws Exception {
        OctetKeyPair keyPair =
                new OctetKeyPairGenerator(Curve.Ed25519).keyID("shareride-test-key").generate();
        JwtTokenVerifier verifier =
                new JwtTokenVerifier(keyPair.toPublicJWK(), null, Duration.ofSeconds(60));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("jwtTokenVerifier", verifier);
        UserContextFilter secureFilter =
                new UserContextFilter(beanFactory.getBeanProvider(JwtTokenVerifier.class));

        UUID accountId = UUID.randomUUID();
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
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(900)))
                        .claim(JwtClaimsConstant.CLAIM_EMAIL, "authentic@shareride.com")
                        .claim(JwtClaimsConstant.CLAIM_ROLES, List.of("ROLE_ADMIN"))
                        .claim(JwtClaimsConstant.CLAIM_PERMISSIONS, List.of("account:admin"))
                        .build();

        SignedJWT signedJwt = new SignedJWT(header, claimsSet);
        signedJwt.sign(new Ed25519Signer(keyPair));
        String validToken = signedJwt.serialize();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + validToken);
        // Even if spoofed header is passed, token claims take precedence
        request.addHeader(UserContextFilter.USER_ID_HEADER, "spoofed-user");
        request.addHeader(UserContextFilter.USER_ROLES_HEADER, "ROLE_RIDER");

        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(
                request,
                response,
                (req, res) -> {
                    UserContext ctx = UserContextHolder.getContext();
                    assertNotNull(ctx);
                    assertEquals(accountId.toString(), ctx.getUserId());
                    assertEquals("authentic@shareride.com", ctx.getEmail());
                    assertTrue(ctx.hasRole("ROLE_ADMIN"));
                    assertTrue(ctx.hasPermission("account:admin"));
                });
    }

    @Test
    @DisplayName("Microservice Defense-in-Depth: Invalid Bearer token throws UnauthorizedException")
    void testDownstreamJwtVerificationFailure() throws Exception {
        OctetKeyPair keyPair =
                new OctetKeyPairGenerator(Curve.Ed25519).keyID("shareride-test-key").generate();
        JwtTokenVerifier verifier =
                new JwtTokenVerifier(keyPair.toPublicJWK(), null, Duration.ofSeconds(60));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("jwtTokenVerifier", verifier);
        UserContextFilter secureFilter =
                new UserContextFilter(beanFactory.getBeanProvider(JwtTokenVerifier.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer invalid-tampered-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                UnauthorizedException.class,
                () -> secureFilter.doFilter(request, response, (req, res) -> {}));
    }
}
