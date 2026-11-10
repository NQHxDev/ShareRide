package com.shareride.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình tham số cho Enterprise JWT trong Auth Service: thời hạn sống của Access Token, Issuer,
 * danh sách Audience, Key ID cho Ed25519, và clock skew tolerance.
 */
@Data
@ConfigurationProperties(prefix = "shareride.security.jwt")
public class JwtProperties {

    /** Tên Issuer đại diện cho định danh cơ quan cấp phát JWT. Mặc định: shareride-auth. */
    private String issuer = "shareride-auth";

    /** Danh sách Audiences được phép tiêu thụ token. */
    private List<String> audiences = new ArrayList<>(List.of("shareride-platform"));

    /** Thời hạn hiệu lực của Access Token. Mặc định: 15 phút theo chuẩn Enterprise Security. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Định danh của khóa ký số trong Header JWT (kid). Mặc định: shareride-auth-key-1. */
    private String keyId = "shareride-auth-key-1";

    /**
     * Chuỗi khóa ký riêng tư định dạng JWK JSON (nếu cấu hình cố định). Nếu bỏ trống, service sẽ tự
     * động sinh cặp khóa Ed25519 lúc khởi động.
     */
    private String privateKeyJwk;

    /** Độ lệch thời gian cho phép khi kiểm tra exp và nbf do trôi đồng hồ giữa các node mạng. */
    private Duration clockSkew = Duration.ofSeconds(60);

    /** Phiên bản schema của token. Mặc định: 1.0. */
    private String version = "1.0";
}
