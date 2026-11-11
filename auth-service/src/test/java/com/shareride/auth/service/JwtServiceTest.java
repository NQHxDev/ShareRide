package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.shareride.auth.config.JwtProperties;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.Session;
import com.shareride.auth.service.impl.JwtServiceImpl;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.security.jwt.JwtClaimsConstant;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import com.shareride.common.security.jwt.JwtUserClaims;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtProperties jwtProperties;
    private OctetKeyPair octetKeyPair;
    private JwtTokenVerifier jwtTokenVerifier;
    private JwtService jwtService;

    @Mock private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() throws Exception {
        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("shareride-auth");
        jwtProperties.setAudiences(List.of("shareride-platform"));
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(15));
        jwtProperties.setKeyId("shareride-auth-key-1");
        jwtProperties.setClockSkew(Duration.ofSeconds(60));
        jwtProperties.setVersion("1.0");

        octetKeyPair =
                new OctetKeyPairGenerator(Curve.Ed25519)
                        .keyID(jwtProperties.getKeyId())
                        .keyUse(KeyUse.SIGNATURE)
                        .generate();

        jwtTokenVerifier =
                new JwtTokenVerifier(
                        octetKeyPair.toPublicJWK(),
                        tokenBlacklistService,
                        jwtProperties.getClockSkew());

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("tokenBlacklistService", tokenBlacklistService);

        jwtService =
                new JwtServiceImpl(
                        jwtProperties,
                        octetKeyPair,
                        jwtTokenVerifier,
                        beanFactory.getBeanProvider(TokenBlacklistService.class));
    }

    private Account createSampleAccount(UUID accountId) {
        Permission rideCreate =
                Permission.builder()
                        .code("ride:create")
                        .name("Create Ride")
                        .description("Can request a ride")
                        .build();

        Permission rideRead =
                Permission.builder()
                        .code("ride:read")
                        .name("Read Ride")
                        .description("Can view ride status")
                        .build();

        Role riderRole =
                Role.builder()
                        .code("ROLE_RIDER")
                        .name("Rider")
                        .permissions(Set.of(rideCreate, rideRead))
                        .build();

        Account account =
                Account.builder()
                        .email("rider@shareride.com")
                        .phoneNumber("+84988111222")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of(riderRole))
                        .build();
        account.setId(accountId);
        return account;
    }

    private Session createSampleSession(UUID sessionId, UUID accountId) {
        Session session =
                Session.builder()
                        .accountId(accountId)
                        .deviceId("iPhone-15-Pro-Max")
                        .deviceName("My iPhone")
                        .ipAddress("127.0.0.1")
                        .build();
        session.setId(sessionId);
        return session;
    }

    @Test
    @DisplayName(
            "generateAccessToken should issue a valid EdDSA signed JWT with full enterprise claims")
    void testGenerateAccessTokenSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Account account = createSampleAccount(accountId);
        Session session = createSampleSession(sessionId, accountId);

        String token = jwtService.generateAccessToken(account, session);
        assertNotNull(token);

        // Parse without verification to inspect raw headers and claims
        SignedJWT signedJwt = SignedJWT.parse(token);
        assertEquals(
                JwtClaimsConstant.ALGORITHM_EDDSA, signedJwt.getHeader().getAlgorithm().getName());
        assertEquals(
                JwtClaimsConstant.HEADER_TYPE_AT_JWT, signedJwt.getHeader().getType().getType());
        assertEquals("shareride-auth-key-1", signedJwt.getHeader().getKeyID());

        // Validate cryptographically via JwtService
        JwtUserClaims claims = jwtService.validateToken(token);
        assertNotNull(claims);
        assertNotNull(claims.jti(), "JTI must not be null");
        assertEquals(accountId, claims.accountId());
        assertEquals("rider@shareride.com", claims.email());
        assertEquals(sessionId, claims.sessionId());
        assertEquals("iPhone-15-Pro-Max", claims.deviceId());
        assertEquals("shareride-auth", claims.issuer());
        assertTrue(claims.audiences().contains("shareride-platform"));
        assertEquals("1.0", claims.version());
        assertEquals("default", claims.tenantId());

        // RBAC roles and permissions
        assertTrue(claims.hasRole("ROLE_RIDER"));
        assertTrue(claims.hasRole("RIDER"));
        assertFalse(claims.hasRole("ROLE_DRIVER"));
        assertTrue(claims.hasPermission("ride:create"));
        assertTrue(claims.hasPermission("ride:read"));
        assertFalse(claims.hasPermission("admin:delete"));
        assertFalse(claims.isExpired());
    }

    @Test
    @DisplayName("validateToken should reject token with tampered payload")
    void testValidateTamperedToken() {
        UUID accountId = UUID.randomUUID();
        Account account = createSampleAccount(accountId);
        String token = jwtService.generateAccessToken(account, null);

        // Tamper with serialized token by modifying payload segment
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        // Replace one character in the payload
        char originalChar = parts[1].charAt(10);
        char tamperedChar = (originalChar == 'a') ? 'b' : 'a';
        String tamperedPayload = parts[1].substring(0, 10) + tamperedChar + parts[1].substring(11);
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        UnauthorizedException exception =
                assertThrows(
                        UnauthorizedException.class, () -> jwtService.validateToken(tamperedToken));
        assertTrue(
                exception.getMessage().contains("Invalid JWT signature")
                        || exception.getMessage().contains("tampered"));
    }

    @Test
    @DisplayName("validateToken should reject blacklisted token via JTI")
    void testValidateBlacklistedToken() {
        UUID accountId = UUID.randomUUID();
        Account account = createSampleAccount(accountId);
        String token = jwtService.generateAccessToken(account, null);

        String jti = jwtService.extractJti(token).orElseThrow();
        when(tokenBlacklistService.isBlacklisted(eq(jti))).thenReturn(true);

        UnauthorizedException exception =
                assertThrows(UnauthorizedException.class, () -> jwtService.validateToken(token));
        assertTrue(exception.getMessage().contains("revoked"));
    }

    @Test
    @DisplayName(
            "revokeToken should extract JTI and register in TokenBlacklistService with remaining"
                    + " TTL")
    void testRevokeTokenSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account = createSampleAccount(accountId);
        String token = jwtService.generateAccessToken(account, null);

        String jti = jwtService.extractJti(token).orElseThrow();
        jwtService.revokeToken(token);

        verify(tokenBlacklistService).blacklistToken(eq(jti), any(Duration.class));
    }

    @Test
    @DisplayName("getJwks should return RFC 7517 public keys without exposing private key")
    void testGetJwksPublicKeySafety() {
        Map<String, Object> jwks = jwtService.getJwks();
        assertNotNull(jwks);
        assertTrue(jwks.containsKey("keys"));

        Object keysObj = jwks.get("keys");
        assertInstanceOf(List.class, keysObj);
        List<?> keys = (List<?>) keysObj;
        assertFalse(keys.isEmpty());

        Object keyObj = keys.get(0);
        assertInstanceOf(Map.class, keyObj);
        Map<?, ?> key = (Map<?, ?>) keyObj;
        assertEquals("OKP", key.get("kty"));
        assertEquals("Ed25519", key.get("crv"));
        assertEquals("shareride-auth-key-1", key.get("kid"));
        assertEquals("sig", key.get("use"));
        assertNotNull(key.get("x"), "Public key coordinate x must be present");
        assertNull(key.get("d"), "Private key parameter 'd' must not be exposed in JWKS!");
    }

    @Test
    @DisplayName("extractJti and extractExpiration should parse safely without throwing")
    void testExtractSafeHelpers() {
        UUID accountId = UUID.randomUUID();
        Account account = createSampleAccount(accountId);
        String token = jwtService.generateAccessToken(account, null);

        Optional<String> jtiOpt = jwtService.extractJti(token);
        assertTrue(jtiOpt.isPresent());

        Optional<Instant> expOpt = jwtService.extractExpiration(token);
        assertTrue(expOpt.isPresent());
        assertTrue(expOpt.get().isAfter(Instant.now()));

        // Invalid inputs
        assertTrue(jwtService.extractJti(null).isEmpty());
        assertTrue(jwtService.extractJti("invalid-token-string").isEmpty());
        assertTrue(jwtService.extractExpiration(null).isEmpty());
        assertTrue(jwtService.extractExpiration("invalid-token-string").isEmpty());
    }

    @Test
    @DisplayName("generateAccessToken: Throws IllegalArgumentException when account is null")
    void testGenerateAccessTokenNullAccount() {
        assertThrows(
                IllegalArgumentException.class, () -> jwtService.generateAccessToken(null, null));
    }

    @Test
    @DisplayName("generateAccessToken: Uses RolePermissionCacheService when available")
    void testGenerateAccessTokenWithCacheService() {
        RolePermissionCacheService cacheService =
                org.mockito.Mockito.mock(RolePermissionCacheService.class);
        when(cacheService.getPermissionsByRoleCodes(any()))
                .thenReturn(Set.of("cached:perm1", "cached:perm2"));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("tokenBlacklistService", tokenBlacklistService);
        beanFactory.addBean("rolePermissionCacheService", cacheService);

        JwtService cachedJwtService =
                new JwtServiceImpl(
                        jwtProperties,
                        octetKeyPair,
                        jwtTokenVerifier,
                        beanFactory.getBeanProvider(TokenBlacklistService.class),
                        beanFactory.getBeanProvider(RolePermissionCacheService.class));

        Account account = createSampleAccount(UUID.randomUUID());
        String token = cachedJwtService.generateAccessToken(account, null);
        assertNotNull(token);
        verify(cacheService).getPermissionsByRoleCodes(any());
    }

    @Test
    @DisplayName("revokeToken: Edge cases (null, raw JTI, already expired, no blacklist service)")
    void testRevokeTokenEdgeCases() throws Exception {
        jwtService.revokeToken(null);
        jwtService.revokeToken("   ");
        jwtService.revokeByJti(null, Duration.ofMinutes(5));

        // Revoke by raw JTI
        jwtService.revokeToken("raw-uuid-jti-999");
        verify(tokenBlacklistService).blacklistToken(eq("raw-uuid-jti-999"), any());

        // Revoke when blacklist service is unavailable
        JwtService noBlacklistService =
                new JwtServiceImpl(
                        jwtProperties,
                        octetKeyPair,
                        jwtTokenVerifier,
                        new StaticListableBeanFactory()
                                .getBeanProvider(TokenBlacklistService.class));
        noBlacklistService.revokeByJti("some-jti", Duration.ofMinutes(5));

        // Expired token skipping blacklist
        Instant past = Instant.now().minusSeconds(1000);
        JWSHeader header =
                new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.EdDSA)
                        .type(
                                new com.nimbusds.jose.JOSEObjectType(
                                        JwtClaimsConstant.HEADER_TYPE_AT_JWT))
                        .keyID(octetKeyPair.getKeyID())
                        .build();
        com.nimbusds.jwt.JWTClaimsSet claims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .jwtID(UUID.randomUUID().toString())
                        .expirationTime(Date.from(past))
                        .build();
        SignedJWT expiredJwt = new SignedJWT(header, claims);
        expiredJwt.sign(new com.nimbusds.jose.crypto.Ed25519Signer(octetKeyPair));
        String expiredToken = expiredJwt.serialize();

        jwtService.revokeToken(expiredToken);
    }
}
