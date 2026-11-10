package com.shareride.gateway.config;

import com.nimbusds.jose.jwk.OctetKeyPair;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.security.jwt.JwtClaimsConstant;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import java.text.ParseException;
import java.time.Duration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enterprise asymmetric JWT configuration for API Gateway perimeter security.
 *
 * <p>Loads the public EdDSA (Ed25519) JSON Web Key (JWK) and initializes {@link JwtTokenVerifier}
 * for fast, stateless cryptographic token verification at the API boundary without needing the
 * private signing key.
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "shareride.security.jwt")
public class JwtSecurityConfig {

    /** Public RFC 8037 OctetKeyPair JWK JSON string containing the Ed25519 curve parameter 'x'. */
    private String publicKeyJwk;

    /** Allowed temporal clock skew tolerance for exp and nbf claims validation. */
    private Duration clockSkew = Duration.ofSeconds(60);

    /** Expected token issuer claim (iss). */
    private String issuer = "shareride-auth";

    /**
     * Creates a {@link JwtTokenVerifier} bean configured with the public Ed25519 key and optional
     * distributed blacklist check. If the public key is not configured, returns null so perimeter
     * verification is safely disabled.
     *
     * @param tokenBlacklistServiceProvider provider for distributed blacklist service if available
     * @return initialized stateless JWT verifier or null if not configured
     * @throws ParseException if the public JWK string cannot be parsed
     */
    @Bean
    @ConditionalOnProperty(prefix = "shareride.security.jwt", name = "public-key-jwk")
    public JwtTokenVerifier jwtTokenVerifier(
            ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider)
            throws ParseException {
        if (publicKeyJwk == null || publicKeyJwk.isBlank()) {
            log.info(
                    "shareride.security.jwt.public-key-jwk is not configured; perimeter JWT"
                            + " verification disabled");
            return null;
        }
        log.info("Configuring Gateway Ed25519 JwtTokenVerifier with provided public JWK");
        OctetKeyPair octetKeyPair = OctetKeyPair.parse(stripEnclosingQuotes(publicKeyJwk));
        return new JwtTokenVerifier(
                octetKeyPair.toPublicJWK(),
                tokenBlacklistServiceProvider.getIfAvailable(),
                clockSkew != null ? clockSkew : JwtClaimsConstant.DEFAULT_CLOCK_SKEW);
    }

    private static String stripEnclosingQuotes(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
