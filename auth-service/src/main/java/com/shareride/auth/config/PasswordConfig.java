package com.shareride.auth.config;

import com.shareride.auth.util.PasswordUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cấu hình cơ chế mã hóa mật khẩu sử dụng thuật toán Argon2id (RFC 9106 / OWASP recommendation) kết
 * hợp khóa bí mật Secret Pepper.
 *
 * <p>Cung cấp bean {@link PasswordEncoder} với cấu hình Argon2id và Secret Pepper quản lý tập trung
 * qua {@link PasswordUtils}.
 */
@Configuration
@EnableConfigurationProperties(PasswordProperties.class)
public class PasswordConfig {

    public PasswordConfig(PasswordProperties passwordProperties) {
        if (passwordProperties != null
                && passwordProperties.getSecret() != null
                && !passwordProperties.getSecret().isBlank()) {
            PasswordUtils.setSecret(passwordProperties.getSecret());
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordUtils.getEncoder();
    }
}
