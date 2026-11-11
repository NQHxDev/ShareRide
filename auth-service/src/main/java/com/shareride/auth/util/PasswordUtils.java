package com.shareride.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tiện ích mã hóa và đối soát mật khẩu sử dụng thuật toán Argon2id chuẩn hóa kết hợp Secret Pepper
 * (RFC 9106 / OWASP recommendation).
 *
 * <p>Cung cấp các hàm tĩnh tiện ích thực hiện băm mật khẩu và đối soát tính hợp lệ của mật khẩu
 * người dùng với tham số Argon2id tối ưu kết hợp tiền xử lý bí mật HMAC-SHA256 (Pepper Secret):
 *
 * <ul>
 *   <li><b>Pepper Secret:</b> Bổ sung khóa bí mật máy chủ qua HMAC-SHA256 trước khi băm, ngăn chặn
 *       triệt để tấn công vét cạn ngoại tuyến (offline brute-force / GPU cracking) ngay cả khi lộ
 *       cơ sở dữ liệu.
 *   <li><b>Argon2id Parameters:</b> 16 bytes salt, 32 bytes hash length, 1 parallelism thread, 16
 *       MiB (16384 KiB) memory cost, 2 iterations.
 * </ul>
 */
public final class PasswordUtils {

    private static final String DEFAULT_PEPPER_SECRET =
            "shareride_argon2id_default_pepper_secret_2026";
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    private static final Argon2PasswordEncoder DELEGATE =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    private static volatile String configuredSecret = null;

    private static final PasswordEncoder PEPPERED_ENCODER =
            new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    return hashPassword(rawPassword != null ? rawPassword.toString() : null);
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return verifyPassword(
                            rawPassword != null ? rawPassword.toString() : null, encodedPassword);
                }

                @Override
                public boolean upgradeEncoding(String encodedPassword) {
                    return DELEGATE.upgradeEncoding(encodedPassword);
                }
            };

    private PasswordUtils() {
        // Utility class - ngăn chặn khởi tạo instance
    }

    /**
     * Cấu hình khóa bí mật (Secret Pepper) toàn cục dùng cho các thao tác mã hóa và kiểm tra.
     *
     * @param secret chuỗi bí mật pepper
     */
    public static void setSecret(String secret) {
        configuredSecret = secret;
    }

    /**
     * Lấy khóa bí mật (Secret Pepper) hiện thời, ưu tiên giá trị được cấu hình qua Spring
     * environment, biến môi trường {@code PASSWORD_SECRET}, hoặc giá trị mặc định.
     *
     * @return chuỗi bí mật pepper
     */
    public static String getSecret() {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            return configuredSecret;
        }
        String envSecret = System.getenv("PASSWORD_HASH_PEPPER");
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret;
        }
        return DEFAULT_PEPPER_SECRET;
    }

    /**
     * Băm mật khẩu thô sử dụng thuật toán Argon2id kết hợp khóa bí mật mặc định đã cấu hình.
     *
     * @param rawPassword mật khẩu dạng thô chưa mã hóa
     * @return chuỗi băm Argon2id định dạng {@code $argon2id$v=19$m=16384,t=2,p=1$...}
     * @throws IllegalArgumentException nếu mật khẩu thô là null
     */
    public static String hashPassword(String rawPassword) {
        return hashPassword(rawPassword, getSecret());
    }

    /**
     * Băm mật khẩu thô sử dụng thuật toán Argon2id kết hợp khóa bí mật (Secret Pepper) chỉ định.
     *
     * @param rawPassword mật khẩu dạng thô chưa mã hóa
     * @param secret khóa bí mật pepper
     * @return chuỗi băm Argon2id định dạng {@code $argon2id$v=19$m=16384,t=2,p=1$...}
     * @throws IllegalArgumentException nếu mật khẩu thô là null
     */
    public static String hashPassword(String rawPassword, String secret) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Raw password cannot be null");
        }
        String peppered = applyPepper(rawPassword, secret);
        return DELEGATE.encode(peppered);
    }

    /**
     * Đối soát mật khẩu người dùng nhập vào với chuỗi băm Argon2id sử dụng khóa bí mật mặc định đã
     * cấu hình.
     *
     * @param rawPassword mật khẩu dạng thô cần kiểm tra
     * @param encodedPassword chuỗi băm Argon2id đã lưu trong cơ sở dữ liệu
     * @return true nếu mật khẩu và khóa bí mật trùng khớp, ngược lại false
     */
    public static boolean verifyPassword(String rawPassword, String encodedPassword) {
        return verifyPassword(rawPassword, encodedPassword, getSecret());
    }

    /**
     * Đối soát mật khẩu người dùng nhập vào với chuỗi băm Argon2id sử dụng khóa bí mật chỉ định.
     *
     * @param rawPassword mật khẩu dạng thô cần kiểm tra
     * @param encodedPassword chuỗi băm Argon2id đã lưu trong cơ sở dữ liệu
     * @param secret khóa bí mật pepper
     * @return true nếu mật khẩu và khóa bí mật trùng khớp, ngược lại false
     */
    public static boolean verifyPassword(
            String rawPassword, String encodedPassword, String secret) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        String peppered = applyPepper(rawPassword, secret);
        return DELEGATE.matches(peppered, encodedPassword);
    }

    /**
     * Trả về đối tượng {@link PasswordEncoder} tích hợp sẵn cơ chế Secret Pepper và thuật toán
     * Argon2id chuẩn hóa.
     *
     * @return instance {@link PasswordEncoder}
     */
    public static PasswordEncoder getEncoder() {
        return PEPPERED_ENCODER;
    }

    private static String applyPepper(CharSequence rawPassword, String secret) {
        if (secret == null || secret.isBlank()) {
            return rawPassword.toString();
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(
                    "Failed to calculate HMAC-SHA256 pepper for password hashing", e);
        }
    }
}
