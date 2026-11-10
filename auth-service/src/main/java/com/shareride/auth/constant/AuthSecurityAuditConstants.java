package com.shareride.auth.constant;

/** Định nghĩa tập trung các hằng số liên quan đến Security Audit Log trong phân hệ Auth Service. */
public final class AuthSecurityAuditConstants {

    private AuthSecurityAuditConstants() {
        // Ngăn chặn khởi tạo utility class
    }

    /** Các loại sự kiện an toàn thông tin (Security Audit Event Types). */
    public static final class EventType {
        private EventType() {}

        public static final String ACCOUNT_REGISTRATION = "ACCOUNT_REGISTRATION";
        public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
        public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
        public static final String LOGOUT = "LOGOUT";
        public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
        public static final String SESSION_REVOKED = "SESSION_REVOKED";
    }

    /** Trạng thái kết quả của sự kiện bảo mật (Security Audit Status). */
    public static final class Status {
        private Status() {}

        public static final String SUCCESS = "SUCCESS";
        public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
        public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
        public static final String INVALID_TOKEN = "INVALID_TOKEN";
    }
}
