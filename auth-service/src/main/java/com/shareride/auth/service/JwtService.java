package com.shareride.auth.service;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.Session;
import com.shareride.common.security.jwt.JwtUserClaims;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Enterprise JWT Service handling Edwards-curve Digital Signature (EdDSA / Ed25519) token issuance,
 * cryptographic verification, JTI-based real-time revocation, and JWKS publication.
 */
public interface JwtService {

    /**
     * Generates an enterprise-standard Access Token signed with Ed25519 containing RFC 7519 claims
     * and custom security context.
     *
     * @param account the authenticated account
     * @param session the active device session (optional, can be null)
     * @return signed, compact serialized JWT
     */
    String generateAccessToken(Account account, Session session);

    /**
     * Verifies the cryptographic signature, tamper-proof integrity, temporal claims, and
     * distributed Valkey JTI blacklist status of the access token.
     *
     * @param token serialized compact JWT
     * @return verified {@link JwtUserClaims}
     */
    JwtUserClaims validateToken(String token);

    /**
     * Revokes an access token immediately by extracting its JTI and registering it in the
     * distributed Valkey blacklist for its remaining lifespan.
     *
     * @param token serialized compact JWT or JTI identifier
     */
    void revokeToken(String token);

    /**
     * Revokes a token by explicit JTI and remaining duration.
     *
     * @param jti JWT ID to blacklist
     * @param remainingTtl duration to keep in the distributed blacklist
     */
    void revokeByJti(String jti, Duration remainingTtl);

    /**
     * Returns the RFC 7517 compliant JSON Web Key Set (JWKS) exposing the public Ed25519 key.
     *
     * @return JSON structure containing the public key set
     */
    Map<String, Object> getJwks();

    /**
     * Returns the configured lifespan of access tokens.
     *
     * @return Duration of access token validity
     */
    Duration getAccessTokenTtl();

    /**
     * Safely extracts the JTI claim without verifying the signature (e.g. for audit or logout).
     *
     * @param token serialized compact JWT
     * @return Optional containing JTI if present
     */
    Optional<String> extractJti(String token);

    /**
     * Safely extracts the expiration Instant claim without verifying the signature.
     *
     * @param token serialized compact JWT
     * @return Optional containing expiration Instant if present
     */
    Optional<Instant> extractExpiration(String token);
}
