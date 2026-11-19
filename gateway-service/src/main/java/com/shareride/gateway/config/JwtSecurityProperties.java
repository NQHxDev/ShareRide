package com.shareride.gateway.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for JWT security in the API Gateway.
 *
 * <p>Holds the public EdDSA (Ed25519) key configuration and related JWT validation parameters
 * loaded from application configuration files.
 *
 * <p>Properties are bound from {@code shareride.security.jwt.*} configuration namespace.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "shareride.security.jwt")
public class JwtSecurityProperties {

    /**
     * Public RFC 8037 OctetKeyPair JWK JSON string containing the Ed25519 curve parameter 'x'.
     *
     * <p>This is the public key used to verify JWT tokens signed by the auth-service.
     */
    private String publicKeyJwk;

    /**
     * Allowed temporal clock skew tolerance for exp and nbf claims validation.
     *
     * <p>Default: 60 seconds
     */
    private Duration clockSkew = Duration.ofSeconds(60);

    /**
     * Expected token issuer claim (iss) for JWT validation.
     *
     * <p>Default: "shareride-auth"
     */
    private String issuer = "shareride-auth";
}
