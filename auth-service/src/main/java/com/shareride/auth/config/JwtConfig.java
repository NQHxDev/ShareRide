package com.shareride.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import java.text.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khởi tạo và quản lý cấu hình mật mã học EdDSA (Ed25519) cho hệ thống xác thực.
 *
 * <p>Cung cấp bean {@link OctetKeyPair} dùng để ký số và bean {@link JwtTokenVerifier} dùng để xác
 * thực chữ ký và kiểm tra thu hồi token qua JTI.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public OctetKeyPair octetKeyPair(JwtProperties properties)
            throws JOSEException, ParseException {
        if (properties.getPrivateKeyJwk() != null && !properties.getPrivateKeyJwk().isBlank()) {
            log.info(
                    "Loading pre-configured Ed25519 OctetKeyPair with kid [{}]",
                    properties.getKeyId());
            return OctetKeyPair.parse(stripEnclosingQuotes(properties.getPrivateKeyJwk()));
        }

        log.info(
                "Generating dynamic Edwards-curve Ed25519 key pair with kid [{}] for JWT signing",
                properties.getKeyId());
        return new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID(properties.getKeyId())
                .keyUse(KeyUse.SIGNATURE)
                .generate();
    }

    @Bean
    public JwtTokenVerifier jwtTokenVerifier(
            OctetKeyPair octetKeyPair,
            ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
            JwtProperties properties) {
        return new JwtTokenVerifier(
                octetKeyPair.toPublicJWK(),
                tokenBlacklistServiceProvider.getIfAvailable(),
                properties.getClockSkew());
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
