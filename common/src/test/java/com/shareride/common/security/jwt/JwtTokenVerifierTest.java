package com.shareride.common.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.context.UserContext;
import com.shareride.common.exception.UnauthorizedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtTokenVerifierTest {

    private OctetKeyPair keyPair;
    private JwtTokenVerifier verifier;

    @Mock private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() throws JOSEException {
        keyPair = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key-1").generate();
        verifier = new JwtTokenVerifier(keyPair, tokenBlacklistService, Duration.ofSeconds(5));
    }

    private String createToken(
            String jti,
            UUID accountId,
            String email,
            List<String> roles,
            List<String> permissions,
            UUID sessionId,
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt)
            throws JOSEException {
        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                        .type(new JOSEObjectType(JwtClaimsConstant.HEADER_TYPE_AT_JWT))
                        .keyID(keyPair.getKeyID())
                        .build();

        JWTClaimsSet.Builder builder =
                new JWTClaimsSet.Builder()
                        .jwtID(jti)
                        .subject(accountId.toString())
                        .issuer("shareride-auth")
                        .audience("shareride-platform")
                        .issueTime(Date.from(issuedAt))
                        .notBeforeTime(Date.from(notBefore))
                        .expirationTime(Date.from(expiresAt))
                        .claim(JwtClaimsConstant.CLAIM_EMAIL, email)
                        .claim(JwtClaimsConstant.CLAIM_ROLES, roles)
                        .claim(JwtClaimsConstant.CLAIM_PERMISSIONS, permissions)
                        .claim(JwtClaimsConstant.CLAIM_SESSION_ID, sessionId.toString())
                        .claim(JwtClaimsConstant.CLAIM_DEVICE_ID, "device-iphone-15")
                        .claim(JwtClaimsConstant.CLAIM_TENANT_ID, "vn-hcm")
                        .claim(JwtClaimsConstant.CLAIM_VERSION, "1.0");

        SignedJWT signedJWT = new SignedJWT(header, builder.build());
        signedJWT.sign(new Ed25519Signer(keyPair));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("Verify: Should successfully verify valid Ed25519 signed token and extract claims")
    void testVerifyValidToken() throws Exception {
        String jti = UuidCreator.getTimeOrderedEpoch().toString();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        String token =
                createToken(
                        jti,
                        accountId,
                        "rider@shareride.com",
                        List.of("ROLE_RIDER", "ROLE_PASSENGER"),
                        List.of("ride:create", "ride:read"),
                        sessionId,
                        now,
                        now.minusSeconds(10),
                        now.plusSeconds(300));

        JwtUserClaims claims = verifier.verify(token);

        assertNotNull(claims);
        assertEquals(jti, claims.jti());
        assertEquals(accountId, claims.accountId());
        assertEquals("rider@shareride.com", claims.email());
        assertEquals(sessionId, claims.sessionId());
        assertEquals("device-iphone-15", claims.deviceId());
        assertEquals("vn-hcm", claims.tenantId());
        assertEquals("shareride-auth", claims.issuer());
        assertTrue(claims.hasRole("ROLE_RIDER"));
        assertTrue(claims.hasRole("rider")); // case-insensitive prefix test
        assertTrue(claims.hasPermission("ride:create"));
        assertFalse(claims.isExpired());

        UserContext userContext = claims.toUserContext();
        assertEquals(accountId.toString(), userContext.getUserId());
        assertEquals("rider@shareride.com", userContext.getEmail());
        assertEquals(sessionId.toString(), userContext.getSessionId());
        assertTrue(userContext.hasRole("ROLE_RIDER"));
    }

    @Test
    @DisplayName("Tamper Resistance: Modifying 1 byte in payload must fail verification")
    void testTamperedPayloadFailsVerification() throws Exception {
        String jti = UuidCreator.getTimeOrderedEpoch().toString();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        String validToken =
                createToken(
                        jti,
                        accountId,
                        "rider@shareride.com",
                        List.of("ROLE_RIDER"),
                        List.of("ride:create"),
                        sessionId,
                        now,
                        now.minusSeconds(5),
                        now.plusSeconds(300));

        String[] parts = validToken.split("\\.");
        assertEquals(3, parts.length);

        // Tamper with payload (modify a character in the middle part)
        char[] payloadChars = parts[1].toCharArray();
        payloadChars[5] = (payloadChars[5] == 'A') ? 'B' : 'A';
        String tamperedPayload = new String(payloadChars);

        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        UnauthorizedException exception =
                assertThrows(UnauthorizedException.class, () -> verifier.verify(tamperedToken));
        assertTrue(
                exception.getMessage().contains("tampered")
                        || exception.getMessage().contains("Invalid JWT signature"));
    }

    @Test
    @DisplayName("Temporal Check: Expired token must throw UnauthorizedException")
    void testExpiredTokenFails() throws Exception {
        String jti = UuidCreator.getTimeOrderedEpoch().toString();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(60);

        String expiredToken =
                createToken(
                        jti,
                        accountId,
                        "rider@shareride.com",
                        List.of("ROLE_RIDER"),
                        List.of("ride:create"),
                        sessionId,
                        past.minusSeconds(300),
                        past.minusSeconds(300),
                        past); // Expired 60s ago, well outside clockSkew 5s

        UnauthorizedException exception =
                assertThrows(UnauthorizedException.class, () -> verifier.verify(expiredToken));
        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Temporal Check: Premature token before nbf must throw UnauthorizedException")
    void testPrematureTokenFails() throws Exception {
        String jti = UuidCreator.getTimeOrderedEpoch().toString();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(60);

        String prematureToken =
                createToken(
                        jti,
                        accountId,
                        "rider@shareride.com",
                        List.of("ROLE_RIDER"),
                        List.of("ride:create"),
                        sessionId,
                        future,
                        future, // Not valid until 60s later
                        future.plusSeconds(300));

        UnauthorizedException exception =
                assertThrows(UnauthorizedException.class, () -> verifier.verify(prematureToken));
        assertTrue(exception.getMessage().contains("not yet valid"));
    }

    @Test
    @DisplayName("JTI Blacklist Revocation: Blacklisted token must be rejected immediately")
    void testBlacklistedTokenFails() throws Exception {
        String jti = UuidCreator.getTimeOrderedEpoch().toString();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        when(tokenBlacklistService.isBlacklisted(jti)).thenReturn(true);

        String token =
                createToken(
                        jti,
                        accountId,
                        "rider@shareride.com",
                        List.of("ROLE_RIDER"),
                        List.of("ride:create"),
                        sessionId,
                        now,
                        now.minusSeconds(5),
                        now.plusSeconds(300));

        UnauthorizedException exception =
                assertThrows(UnauthorizedException.class, () -> verifier.verify(token));
        assertTrue(exception.getMessage().contains("revoked"));
    }

    @Test
    @DisplayName("Extraction: extractJti and extractExpiration should parse without verifying")
    void testExtractJtiAndExpiration() throws Exception {
        String jti = UuidCreator.getTimeOrderedEpoch().toString();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(600);

        String token =
                createToken(
                        jti,
                        accountId,
                        "rider@shareride.com",
                        List.of("ROLE_RIDER"),
                        List.of(),
                        sessionId,
                        Instant.now(),
                        Instant.now().minusSeconds(5),
                        exp);

        Optional<String> extractedJti = verifier.extractJti(token);
        Optional<Instant> extractedExp = verifier.extractExpiration(token);

        assertTrue(extractedJti.isPresent());
        assertEquals(jti, extractedJti.get());

        assertTrue(extractedExp.isPresent());
        assertEquals(exp.getEpochSecond(), extractedExp.get().getEpochSecond());
    }
}
