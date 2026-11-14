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

    /** Tên thuật toán băm mật khẩu chuẩn hóa: {@code "Argon2id"}. */
    public static final String PASSWORD_ALGORITHM_ARGON2ID = "Argon2id";

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

        /** Tiền tố lưu cache Role theo RoleCode trên Valkey: {@code "auth:cache:role:code:"}. */
        public static final String ROLE_CODE_PREFIX = "auth:cache:role:code:";

        /** Tiền tố lưu cache Role theo ID trên Valkey: {@code "auth:cache:role:id:"}. */
        public static final String ROLE_ID_PREFIX = "auth:cache:role:id:";

        /**
         * Tiền tố lưu cache danh sách PermissionCodes của Role trên Valkey: {@code
         * "auth:cache:role-permissions:"}.
         */
        public static final String ROLE_PERMISSIONS_PREFIX = "auth:cache:role-permissions:";

        /**
         * Tiền tố lưu cache Permission theo PermissionCode trên Valkey: {@code
         * "auth:cache:permission:code:"}.
         */
        public static final String PERMISSION_CODE_PREFIX = "auth:cache:permission:code:";

        /** Tiền tố lưu cache danh sách tất cả Roles trên Valkey: {@code "auth:cache:roles:all"}. */
        public static final String ALL_ROLES_KEY = "auth:cache:roles:all";

        /**
         * Tiền tố lưu cache danh sách tất cả Permissions trên Valkey: {@code
         * "auth:cache:permissions:all"}.
         */
        public static final String ALL_PERMISSIONS_KEY = "auth:cache:permissions:all";

        /** Tiền tố rate limit đăng nhập theo IP: {@code "auth:ratelimit:login:ip:"}. */
        public static final String LOGIN_RATE_LIMIT_IP_PREFIX = "auth:ratelimit:login:ip:";

        /** Tiền tố rate limit đăng nhập theo tài khoản: {@code "auth:ratelimit:login:account:"}. */
        public static final String LOGIN_RATE_LIMIT_ACCOUNT_PREFIX =
                "auth:ratelimit:login:account:";
    }

    /** Cấu hình ngưỡng và thời gian giới hạn cho Rate Limiting chống Brute-force. */
    public static final class RateLimit {
        private RateLimit() {}

        /** Tên cấu hình Resilience4j RateLimiter instance cho Login Endpoint. */
        public static final String LOGIN_RATE_LIMITER_NAME = "loginRateLimiter";

        /** Số lần thử đăng nhập sai tối đa theo IP trước khi bị chặn: 5 lần. */
        public static final int MAX_IP_FAILED_ATTEMPTS = 5;

        /** Thời gian khóa tần suất đăng nhập theo IP: 15 phút. */
        public static final Duration IP_RATE_LIMIT_TTL = Duration.ofMinutes(15);

        /** Số lần thử đăng nhập sai tối đa theo tài khoản trước khi bị khóa: 10 lần. */
        public static final int MAX_ACCOUNT_FAILED_ATTEMPTS = 10;

        /** Thời gian theo dõi đăng nhập sai theo tài khoản: 1 giờ. */
        public static final Duration ACCOUNT_RATE_LIMIT_TTL = Duration.ofHours(1);
    }

    /** Cấu hình thời gian sống (TTL) cho cache trên Valkey/Redis. */
    public static final class CacheTtl {
        private CacheTtl() {}

        /** Thời hạn lưu cache cho Role và Permission trên Valkey (6 tiếng). */
        public static final Duration ROLE_PERMISSION_VALIDITY = Duration.ofHours(6);
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
