package com.shareride.gateway.config;

import com.nimbusds.jose.jwk.OctetKeyPair;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import java.text.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enterprise asymmetric JWT configuration for API Gateway perimeter security.
 *
 * <p>Creates {@link JwtTokenVerifier} bean configured with the public EdDSA (Ed25519) JSON Web Key
 * (JWK) for fast, stateless cryptographic token verification at the API boundary without needing
 * the private signing key.
 *
 * <p>Configuration properties are loaded from {@link JwtSecurityProperties}.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JwtSecurityProperties.class)
@RequiredArgsConstructor
public class JwtSecurityConfig {

    private final JwtSecurityProperties jwtProperties;

    /**
     * Creates a {@link JwtTokenVerifier} bean configured with the public Ed25519 key and optional
     * distributed blacklist check.
     *
     * <p>The bean is only created when {@code shareride.security.jwt.public-key-jwk} property is
     * configured.
     *
     * @param tokenBlacklistServiceProvider provider for distributed blacklist service if available
     * @return initialized stateless JWT verifier
     * @throws ParseException if the public JWK string cannot be parsed
     */
    @Bean
    @ConditionalOnProperty(prefix = "shareride.security.jwt", name = "public-key-jwk")
    public JwtTokenVerifier jwtTokenVerifier(
            ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider)
            throws ParseException {

        log.info("Configuring Gateway Ed25519 JwtTokenVerifier with provided public JWK");

        String publicKeyJwk = stripEnclosingQuotes(jwtProperties.getPublicKeyJwk());
        OctetKeyPair octetKeyPair = OctetKeyPair.parse(publicKeyJwk);

        return new JwtTokenVerifier(
                octetKeyPair.toPublicJWK(),
                tokenBlacklistServiceProvider.getIfAvailable(),
                jwtProperties.getClockSkew());
    }

    /**
     * Strips enclosing single or double quotes from a string value.
     *
     * <p>This is a workaround for configuration values that may be accidentally quoted in
     * environment variables or property files.
     *
     * @param input the input string
     * @return the string without enclosing quotes, or null if input is null
     */
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
