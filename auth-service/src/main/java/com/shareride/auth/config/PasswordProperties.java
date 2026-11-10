package com.shareride.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính cấu hình bảo mật mật khẩu cho Auth Service.
 *
 * <p>Quản lý khóa bí mật (secret pepper) và các tham số thuật toán mã hóa mật khẩu Argon2id.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.security.password")
public class PasswordProperties {

    /** Khóa bí mật (Pepper Secret) dùng để gia cố bảo mật khi băm mật khẩu với Argon2id. */
    private String secret = "shareride_argon2id_default_pepper_secret_2026";
}
