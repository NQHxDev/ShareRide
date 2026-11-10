package com.shareride.auth.service.impl;

import com.github.f4b6a3.uuid.UuidCreator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shareride.auth.config.JwtProperties;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.Session;
import com.shareride.auth.service.JwtService;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.security.jwt.JwtClaimsConstant;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import com.shareride.common.security.jwt.JwtUserClaims;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Enterprise implementation of {@link JwtService} issuing and verifying EdDSA (Ed25519) tokens with
 * time-ordered UUIDv7 JTI claims and real-time distributed Valkey blacklist revocation.
 */
@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

    private final JwtProperties jwtProperties;
    private final OctetKeyPair octetKeyPair;
    private final JwtTokenVerifier jwtTokenVerifier;
    private final ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider;
    private final Ed25519Signer signer;

    public JwtServiceImpl(
            JwtProperties jwtProperties,
            OctetKeyPair octetKeyPair,
            JwtTokenVerifier jwtTokenVerifier,
            ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider) {
        this.jwtProperties = jwtProperties;
        this.octetKeyPair = octetKeyPair;
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.tokenBlacklistServiceProvider = tokenBlacklistServiceProvider;
        try {
            this.signer = new Ed25519Signer(octetKeyPair);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to initialize Ed25519 signer", e);
        }
    }

    @Override
    public String generateAccessToken(Account account, Session session) {
        if (account == null) {
            throw new IllegalArgumentException(
                    "Account must not be null when generating access token");
        }

        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.getAccessTokenTtl());
        String jti = UuidCreator.getTimeOrderedEpoch().toString();

        List<String> roleCodes =
                account.getRoles() != null
                        ? account.getRoles().stream()
                                .filter(Objects::nonNull)
                                .map(
                                        role ->
                                                role.getRoleCode() != null
                                                        ? role.getRoleCode().name()
                                                        : null)
                                .filter(Objects::nonNull)
                                .sorted()
                                .toList()
                        : List.of();

        List<String> permissionCodes =
                account.getRoles() != null
                        ? account.getRoles().stream()
                                .filter(Objects::nonNull)
                                .filter(role -> role.getPermissions() != null)
                                .flatMap(role -> role.getPermissions().stream())
                                .filter(Objects::nonNull)
                                .map(
                                        permission ->
                                                permission.getPermissionCode() != null
                                                        ? permission.getPermissionCode().getValue()
                                                        : null)
                                .filter(Objects::nonNull)
                                .distinct()
                                .sorted()
                                .toList()
                        : List.of();

        JWTClaimsSet.Builder claimsBuilder =
                new JWTClaimsSet.Builder()
                        .jwtID(jti)
                        .issuer(jwtProperties.getIssuer())
                        .subject(account.getId() != null ? account.getId().toString() : null)
                        .audience(jwtProperties.getAudiences())
                        .issueTime(Date.from(now))
                        .notBeforeTime(Date.from(now))
                        .expirationTime(Date.from(exp))
                        .claim(JwtClaimsConstant.CLAIM_EMAIL, account.getEmail())
                        .claim(JwtClaimsConstant.CLAIM_ROLES, roleCodes)
                        .claim(JwtClaimsConstant.CLAIM_PERMISSIONS, permissionCodes)
                        .claim(JwtClaimsConstant.CLAIM_VERSION, jwtProperties.getVersion())
                        .claim(JwtClaimsConstant.CLAIM_TENANT_ID, "default");

        if (session != null) {
            if (session.getId() != null) {
                claimsBuilder.claim(JwtClaimsConstant.CLAIM_SESSION_ID, session.getId().toString());
            }
            if (session.getDeviceId() != null) {
                claimsBuilder.claim(JwtClaimsConstant.CLAIM_DEVICE_ID, session.getDeviceId());
            }
        }

        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                        .type(new JOSEObjectType(JwtClaimsConstant.HEADER_TYPE_AT_JWT))
                        .keyID(octetKeyPair.getKeyID())
                        .build();

        SignedJWT signedJwt = new SignedJWT(header, claimsBuilder.build());
        try {
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException e) {
            log.error(
                    "Failed to sign JWT with EdDSA for account [{}]: {}",
                    account.getId(),
                    e.getMessage());
            throw new IllegalStateException("Failed to cryptographically sign JWT", e);
        }
    }

    @Override
    public JwtUserClaims validateToken(String token) {
        return jwtTokenVerifier.verify(token);
    }

    @Override
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        Optional<String> jtiOpt = jwtTokenVerifier.extractJti(token);
        if (jtiOpt.isPresent()) {
            String jti = jtiOpt.get();
            Duration remainingTtl = jwtProperties.getAccessTokenTtl();
            Optional<Instant> expOpt = jwtTokenVerifier.extractExpiration(token);
            if (expOpt.isPresent()) {
                Duration diff = Duration.between(Instant.now(), expOpt.get());
                if (diff.isNegative() || diff.isZero()) {
                    log.debug("Token [{}] is already expired, skipping blacklist entry", jti);
                    return;
                }
                remainingTtl = diff;
            }
            revokeByJti(jti, remainingTtl);
        } else {
            // Support passing raw JTI directly
            revokeByJti(token.trim(), jwtProperties.getAccessTokenTtl());
        }
    }

    @Override
    public void revokeByJti(String jti, Duration remainingTtl) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        TokenBlacklistService blacklistService = tokenBlacklistServiceProvider.getIfAvailable();
        if (blacklistService != null) {
            blacklistService.blacklistToken(jti, remainingTtl);
            log.info(
                    "JWT with JTI [{}] revoked and added to blacklist with TTL {}s",
                    jti,
                    remainingTtl.toSeconds());
        } else {
            log.warn(
                    "TokenBlacklistService is unavailable; JTI [{}] could not be blacklisted", jti);
        }
    }

    @Override
    public Map<String, Object> getJwks() {
        return new JWKSet(octetKeyPair.toPublicJWK()).toJSONObject();
    }

    @Override
    public Duration getAccessTokenTtl() {
        return jwtProperties.getAccessTokenTtl();
    }

    @Override
    public Optional<String> extractJti(String token) {
        return jwtTokenVerifier.extractJti(token);
    }

    @Override
    public Optional<Instant> extractExpiration(String token) {
        return jwtTokenVerifier.extractExpiration(token);
    }
}
