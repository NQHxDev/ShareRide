package com.shareride.auth.constant;

import java.time.Duration;

/**
 * Tập hợp các cấu hình mặc định, thời hạn token, tiền tố Redis lock, và định dạng xác thực trong
 * Auth Service.
 *
 * <p>Lớp này lưu trữ các giá trị hằng số kỹ thuật giúp các kỹ sư tiếp quản dự án dễ dàng tinh chỉnh
 * TTL, quy ước khóa Redis, hoặc các đường dẫn API mà không cần tìm kiếm rải rác trong code.
 *
 * <h3>Hướng dẫn sử dụng cho người tiếp quản dự án:</h3>
 *
 * <pre>{@code
 * // Sử dụng khóa Redis Lock:
 * String lockKey = AuthConstants.RedisLock.REGISTER_EMAIL_PREFIX + normalizedEmail;
 * Duration timeout = AuthConstants.RedisLock.REGISTER_LOCK_TIMEOUT;
 *
 * // Trích xuất Bearer token từ header:
 * if (authHeader != null && authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
 *     String token = authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH);
 * }
 * }</pre>
 */
public final class AuthConstants {

    private AuthConstants() {
        // Ngăn chặn khởi tạo utility class
    }

    /**
     * Loại token chuẩn sử dụng trong Header Authorization và DTO phản hồi xác thực: {@code
     * "Bearer"}.
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /** Tên chuẩn của HTTP Header chứa mã xác thực: {@code "Authorization"}. */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Tiền tố Bearer đi kèm trong header Authorization: {@code "Bearer "}. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Độ dài của tiền tố "Bearer " (7 ký tự), dùng để tách chuỗi lấy raw token an toàn. */
    public static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /** Tên tham số query param truyền refresh token trong request: {@code "refreshToken"}. */
    public static final String PARAM_REFRESH_TOKEN = "refreshToken";

    /** Tên path variable cho định danh UUID: {@code "id"}. */
    public static final String PATH_VARIABLE_ID = "id";

    /**
     * Tên thuật toán băm cryptographic dùng để băm mật khẩu và refresh token: {@code "SHA-256"}.
     */
    public static final String HASH_ALGORITHM_SHA256 = "SHA-256";

    /**
     * Tên định danh người dùng mặc định khi context chưa xác định được UserId: {@code "unknown"}.
     */
    public static final String UNKNOWN_USER_ID = "unknown";

    /** Cấu hình thời gian sống (TTL / Expiration) cho các loại token và phiên làm việc. */
    public static final class TokenTtl {
        private TokenTtl() {
            // Ngăn chặn khởi tạo utility class
        }

        /**
         * Thời hạn hiệu lực của Access Token (1 giờ). Đồng thời là thời gian token bị đưa vào Redis
         * Token Blacklist khi người dùng chủ động logout.
         */
        public static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofHours(1);

        /** Thời hạn hiệu lực của Refresh Token lưu trong Database (7 ngày). */
        public static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);
    }

    /** Cấu hình quy ước khóa và thời gian chờ của Distributed Lock trên Redis. */
    public static final class RedisLock {
        private RedisLock() {
            // Ngăn chặn khởi tạo utility class
        }

        /**
         * Tiền tố Redis key dùng cho Distributed Lock khi đăng ký tài khoản mới: {@code
         * "register:email:"}. Kết hợp với normalized email ({@code "register:email:" + email}) nhằm
         * ngăn chặn race-condition khi có 2 request đăng ký cùng lúc trên nhiều instance.
         */
        public static final String REGISTER_EMAIL_PREFIX = "register:email:";

        /** Thời gian tối đa giữ Redis Distributed Lock khi đăng ký (5 giây). */
        public static final Duration REGISTER_LOCK_TIMEOUT = Duration.ofSeconds(5);
    }

    /** Danh mục các đường dẫn tài nguyên (Resource URLs) được ghi nhận trong Security Audit Log. */
    public static final class AuditResource {
        private AuditResource() {
            // Ngăn chặn khởi tạo utility class
        }

        /** Đường dẫn API đăng ký người dùng: {@code "/api/v1/auth/register"}. */
        public static final String REGISTER = "/api/v1/auth/register";

        /** Đường dẫn API đăng nhập: {@code "/api/v1/auth/login"}. */
        public static final String LOGIN = "/api/v1/auth/login";

        /** Đường dẫn API đăng xuất: {@code "/api/v1/auth/logout"}. */
        public static final String LOGOUT = "/api/v1/auth/logout";
    }

    /**
     * Danh mục các endpoint URL định tuyến trong {@link
     * com.shareride.auth.controller.AuthController}.
     */
    public static final class Endpoint {
        private Endpoint() {
            // Ngăn chặn khởi tạo utility class
        }

        /** Base path của toàn bộ Auth Controller: {@code "/api/v1/auth"}. */
        public static final String BASE_PATH = "/api/v1/auth";

        /** Đường dẫn con đăng ký tài khoản: {@code "/register"}. */
        public static final String REGISTER = "/register";

        /** Đường dẫn con đăng nhập: {@code "/login"}. */
        public static final String LOGIN = "/login";

        /** Đường dẫn con đăng xuất: {@code "/logout"}. */
        public static final String LOGOUT = "/logout";

        /** Đường dẫn con truy vấn thông tin người dùng theo Id: {@code "/users/{id}"}. */
        public static final String USERS_BY_ID = "/users/{id}";
    }
}
