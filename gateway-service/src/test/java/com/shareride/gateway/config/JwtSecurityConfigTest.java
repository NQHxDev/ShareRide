package com.shareride.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@DisplayName("JwtSecurityConfig Unit Tests")
class JwtSecurityConfigTest {

    @Test
    @DisplayName("Should create JwtTokenVerifier bean from public JWK")
    void shouldCreateJwtTokenVerifierFromPublicJwk() throws Exception {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        String jwkJson =
                "\"{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}\"";
        properties.setPublicKeyJwk(jwkJson);
        properties.setClockSkew(Duration.ofSeconds(30));

        JwtSecurityConfig config = new JwtSecurityConfig(properties);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();

        JwtTokenVerifier verifier =
                config.jwtTokenVerifier(beanFactory.getBeanProvider(TokenBlacklistService.class));

        assertThat(verifier).isNotNull();
    }
}
