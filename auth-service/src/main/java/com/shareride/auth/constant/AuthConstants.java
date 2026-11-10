package com.shareride.auth.constant;

import java.time.Duration;

/**
 * Tập hợp các cấu hình mặc định, thời hạn token, tiền tố Redis/Valkey, và định dạng xác thực trong
 * Auth Service.
 */
public final class AuthConstants {

    private AuthConstants() {
        // Ngăn chặn khởi tạo utility class
    }

    /** Loại token chuẩn sử dụng trong Header Authorization: {@code "Bearer"}. */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /** Tên chuẩn của HTTP Header chứa mã xác thực: {@code "Authorization"}. */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Tiền tố Bearer đi kèm trong header Authorization: {@code "Bearer "}. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Độ dài của tiền tố "Bearer " (7 ký tự). */
    public static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /** Tên tham số query param truyền refresh token trong request: {@code "refreshToken"}. */
    public static final String PARAM_REFRESH_TOKEN = "refreshToken";

    /** Tên path variable cho định danh UUID tài khoản: {@code "id"}. */
    public static final String PATH_VARIABLE_ID = "id";

    /** Tên path variable cho định danh session: {@code "sessionId"}. */
    public static final String PATH_VARIABLE_SESSION_ID = "sessionId";

    /** Tên thuật toán băm cryptographic: {@code "SHA-256"}. */
    public static final String HASH_ALGORITHM_SHA256 = "SHA-256";

    /**
     * Tên định danh tài khoản mặc định khi context chưa xác định được AccountId: {@code "unknown"}.
     */
    public static final String UNKNOWN_ACCOUNT_ID = "unknown";

    /** Cấu hình thời gian sống (TTL / Expiration) cho các loại token và phiên làm việc. */
    public static final class TokenTtl {
        private TokenTtl() {}

        /** Thời hạn hiệu lực của Access Token (15 phút). */
        public static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofMinutes(15);

        /** Thời hạn hiệu lực của Refresh Token lưu trong Valkey (7 ngày). */
        public static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);
    }

    /** Cấu hình quy ước khóa và thời gian chờ của Distributed Lock và Cache trên Valkey/Redis. */
    public static final class CacheKey {
        private CacheKey() {}

        /**
         * Tiền tố Distributed Lock khi đăng ký tài khoản mới: {@code "auth:lock:register:email:"}.
         */
        public static final String REGISTER_EMAIL_LOCK_PREFIX = "auth:lock:register:email:";

        /** Thời gian tối đa giữ Redis Distributed Lock khi đăng ký (5 giây). */
        public static final Duration REGISTER_LOCK_TIMEOUT = Duration.ofSeconds(5);

        /** Tiền tố lưu thông tin Refresh Token trên Valkey: {@code "auth:refresh-token:"}. */
        public static final String REFRESH_TOKEN_PREFIX = "auth:refresh-token:";

        /**
         * Tiền tố ánh xạ SessionId -> RefreshTokenHash trên Valkey: {@code "auth:session-token:"}.
         */
        public static final String SESSION_TOKEN_PREFIX = "auth:session-token:";

        /**
         * Tiền tố lưu danh sách SessionIds đang hoạt động của Account trên Valkey: {@code
         * "auth:account-sessions:"}.
         */
        public static final String ACCOUNT_SESSIONS_PREFIX = "auth:account-sessions:";
    }

    /** Danh mục các đường dẫn tài nguyên (Resource URLs) được ghi nhận trong Security Audit Log. */
    public static final class AuditResource {
        private AuditResource() {}

        public static final String REGISTER = "/api/v1/auth/register";
        public static final String LOGIN = "/api/v1/auth/login";
        public static final String LOGOUT = "/api/v1/auth/logout";
        public static final String REFRESH = "/api/v1/auth/refresh";
        public static final String SESSIONS = "/api/v1/auth/sessions";
        public static final String JWKS = "/api/v1/auth/jwks.json";
    }

    /** Danh mục các endpoint URL định tuyến trong {@code AuthController}. */
    public static final class Endpoint {
        private Endpoint() {}

        public static final String BASE_PATH = "/api/v1/auth";
        public static final String REGISTER = "/register";
        public static final String LOGIN = "/login";
        public static final String LOGOUT = "/logout";
        public static final String REFRESH = "/refresh";
        public static final String ACCOUNTS_BY_ID = "/accounts/{id}";
        public static final String SESSIONS = "/sessions";
        public static final String SESSION_BY_ID = "/sessions/{sessionId}";
        public static final String JWKS = "/jwks.json";
    }
}
