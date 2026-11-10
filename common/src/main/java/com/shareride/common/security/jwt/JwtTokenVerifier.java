package com.shareride.common.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.exception.UnauthorizedException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Enterprise stateless JWT Verifier using Edwards-curve Digital Signature (EdDSA / Ed25519) and
 * real-time JTI distributed revocation checks against Valkey/Redis.
 *
 * <p>Validates cryptographic signature, claim tampering, temporal validity (exp, nbf with clock
 * skew tolerance), and distributed blacklist status.
 */
@Slf4j
public class JwtTokenVerifier {

    private final OctetKeyPair publicKey;
    private final TokenBlacklistService tokenBlacklistService;
    private final Duration clockSkew;
    private final Ed25519Verifier verifier;

    public JwtTokenVerifier(OctetKeyPair publicKey) {
        this(publicKey, null, JwtClaimsConstant.DEFAULT_CLOCK_SKEW);
    }

    public JwtTokenVerifier(OctetKeyPair publicKey, TokenBlacklistService tokenBlacklistService) {
        this(publicKey, tokenBlacklistService, JwtClaimsConstant.DEFAULT_CLOCK_SKEW);
    }

    public JwtTokenVerifier(
            OctetKeyPair publicKey,
            TokenBlacklistService tokenBlacklistService,
            Duration clockSkew) {
        if (publicKey == null) {
            throw new IllegalArgumentException("OctetKeyPair public key must not be null");
        }
        this.publicKey = publicKey.toPublicJWK();
        this.tokenBlacklistService = tokenBlacklistService;
        this.clockSkew = clockSkew != null ? clockSkew : JwtClaimsConstant.DEFAULT_CLOCK_SKEW;
        try {
            this.verifier = new Ed25519Verifier(this.publicKey);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to initialize Ed25519 verifier", e);
        }
    }

    /**
     * Cryptographically verifies the EdDSA signature, tamper-proof integrity, temporal claims, and
     * JTI revocation status of an incoming access token.
     *
     * @param tokenString serialized compact JWT
     * @return verified {@link JwtUserClaims}
     * @throws UnauthorizedException if token is invalid, expired, tampered, or blacklisted
     */
    public JwtUserClaims verify(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            throw new UnauthorizedException("Access token is missing or empty");
        }

        SignedJWT signedJwt;
        try {
            signedJwt = SignedJWT.parse(tokenString.trim());
        } catch (ParseException e) {
            log.warn("Failed to parse JWT format: {}", e.getMessage());
            throw new UnauthorizedException("Invalid JWT token format");
        }

        // 1. Verify Algorithm is EdDSA
        if (!JWSAlgorithm.EdDSA.equals(signedJwt.getHeader().getAlgorithm())) {
            log.warn(
                    "Rejected token with invalid algorithm: {}",
                    signedJwt.getHeader().getAlgorithm());
            throw new UnauthorizedException("Unsupported JWT signing algorithm");
        }

        // 2. Cryptographic Signature & Tamper Verification
        try {
            boolean validSignature = signedJwt.verify(verifier);
            if (!validSignature) {
                log.warn("JWT digital signature validation failed - token has been tampered with");
                throw new UnauthorizedException(
                        "Invalid JWT signature: token has been modified or tampered with");
            }
        } catch (JOSEException e) {
            log.warn("Cryptographic verification error: {}", e.getMessage());
            throw new UnauthorizedException("JWT signature verification failed");
        }

        JWTClaimsSet claimsSet;
        try {
            claimsSet = signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            log.warn("Failed to extract JWT claims: {}", e.getMessage());
            throw new UnauthorizedException("Failed to extract claims from JWT");
        }

        Instant now = Instant.now();

        // 3. Temporal Validity Check (exp & nbf with clock skew tolerance)
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime != null) {
            Instant exp = expirationTime.toInstant();
            if (now.isAfter(exp.plus(clockSkew))) {
                log.debug("JWT token is expired: exp={}, now={}", exp, now);
                throw new UnauthorizedException("Access token is expired");
            }
        }

        Date notBeforeTime = claimsSet.getNotBeforeTime();
        if (notBeforeTime != null) {
            Instant nbf = notBeforeTime.toInstant();
            if (now.isBefore(nbf.minus(clockSkew))) {
                log.warn("JWT token is not yet valid: nbf={}, now={}", nbf, now);
                throw new UnauthorizedException("Access token is not yet valid");
            }
        }

        // 4. JTI Distributed Blacklist Revocation Check
        String jti = claimsSet.getJWTID();
        if (jti != null && tokenBlacklistService != null) {
            if (tokenBlacklistService.isBlacklisted(jti)) {
                log.warn("Rejected revoked token with JTI [{}]", jti);
                throw new UnauthorizedException("Access token has been revoked");
            }
        }

        return mapClaims(claimsSet);
    }

    /**
     * Safely extracts the JWT ID (jti) claim from a token without signature verification. Useful
     * during logout or revocation handling.
     *
     * @param tokenString serialized JWT
     * @return Optional containing JTI if present and parsable
     */
    public Optional<String> extractJti(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(tokenString.trim());
            return Optional.ofNullable(signedJwt.getJWTClaimsSet().getJWTID());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Safely extracts the Expiration (exp) claim from a token without signature verification.
     * Useful for computing remaining Redis TTL during token revocation.
     *
     * @param tokenString serialized JWT
     * @return Optional containing expiration Instant if present and parsable
     */
    public Optional<Instant> extractExpiration(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(tokenString.trim());
            Date exp = signedJwt.getJWTClaimsSet().getExpirationTime();
            return exp != null ? Optional.of(exp.toInstant()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts unverified claims from a token string (for audit/diagnostic or revocation purposes).
     */
    public Optional<JwtUserClaims> parseUnverifiedClaims(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(tokenString.trim());
            return Optional.of(mapClaims(signedJwt.getJWTClaimsSet()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public OctetKeyPair getPublicKey() {
        return publicKey;
    }

    private JwtUserClaims mapClaims(JWTClaimsSet claimsSet) {
        UUID accountId = null;
        String sub = claimsSet.getSubject();
        if (sub != null && !sub.isBlank()) {
            try {
                accountId = UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                log.debug("Subject is not a valid UUID: {}", sub);
            }
        }

        UUID sessionId = null;
        Object sidClaim = claimsSet.getClaim(JwtClaimsConstant.CLAIM_SESSION_ID);
        if (sidClaim != null) {
            try {
                sessionId = UUID.fromString(sidClaim.toString());
            } catch (IllegalArgumentException e) {
                log.debug("Session ID claim is not a valid UUID: {}", sidClaim);
            }
        }

        Set<String> roles = extractStringSet(claimsSet, JwtClaimsConstant.CLAIM_ROLES);
        Set<String> permissions = extractStringSet(claimsSet, JwtClaimsConstant.CLAIM_PERMISSIONS);

        String email = null;
        Object emailClaim = claimsSet.getClaim(JwtClaimsConstant.CLAIM_EMAIL);
        if (emailClaim != null) {
            email = emailClaim.toString();
        }

        String did = null;
        Object didClaim = claimsSet.getClaim(JwtClaimsConstant.CLAIM_DEVICE_ID);
        if (didClaim != null) {
            did = didClaim.toString();
        }

        String tid = null;
        Object tidClaim = claimsSet.getClaim(JwtClaimsConstant.CLAIM_TENANT_ID);
        if (tidClaim != null) {
            tid = tidClaim.toString();
        }

        String ver = JwtClaimsConstant.DEFAULT_TOKEN_VERSION;
        Object verClaim = claimsSet.getClaim(JwtClaimsConstant.CLAIM_VERSION);
        if (verClaim != null) {
            ver = verClaim.toString();
        }

        Instant iat =
                claimsSet.getIssueTime() != null ? claimsSet.getIssueTime().toInstant() : null;
        Instant nbf =
                claimsSet.getNotBeforeTime() != null
                        ? claimsSet.getNotBeforeTime().toInstant()
                        : null;
        Instant exp =
                claimsSet.getExpirationTime() != null
                        ? claimsSet.getExpirationTime().toInstant()
                        : null;

        return JwtUserClaims.builder()
                .jti(claimsSet.getJWTID())
                .accountId(accountId)
                .email(email)
                .roles(roles)
                .permissions(permissions)
                .sessionId(sessionId)
                .deviceId(did)
                .tenantId(tid)
                .issuer(claimsSet.getIssuer())
                .audiences(claimsSet.getAudience())
                .issuedAt(iat)
                .notBefore(nbf)
                .expiresAt(exp)
                .version(ver)
                .build();
    }

    private Set<String> extractStringSet(JWTClaimsSet claimsSet, String claimName) {
        Object val = claimsSet.getClaim(claimName);
        if (val instanceof List<?> list) {
            Set<String> set = new HashSet<>();
            for (Object item : list) {
                if (item != null) {
                    set.add(item.toString());
                }
            }
            return set;
        }
        return Set.of();
    }
}
