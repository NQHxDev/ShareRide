package com.shareride.auth.constant;

/**
 * Định nghĩa tập trung mã phản hồi nghiệp vụ (Business Response Codes) và mã lỗi cho Auth Service.
 */
public final class AuthResponseCode {

    private AuthResponseCode() {
        // Ngăn chặn khởi tạo utility class
    }

    /** Mã phản hồi khi tài khoản được tạo mới thành công (HTTP 201 Created). */
    public static final String ACCOUNT_REGISTERED = "ACCOUNT_REGISTERED";

    /** Mã phản hồi khi xác thực và đăng nhập thành công (HTTP 200 OK). */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

    /** Mã phản hồi khi làm mới Access Token thành công (HTTP 200 OK). */
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";

    /** Mã phản hồi khi thu hồi phiên làm việc thành công (HTTP 200 OK). */
    public static final String SESSION_REVOKED = "SESSION_REVOKED";

    /** Mã lỗi khi địa chỉ email đăng ký đã tồn tại trong hệ thống (HTTP 409 Conflict). */
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";

    /** Mã lỗi khi số điện thoại đăng ký đã tồn tại trong hệ thống (HTTP 409 Conflict). */
    public static final String PHONE_ALREADY_EXISTS = "PHONE_ALREADY_EXISTS";

    /** Mã lỗi khi thông tin xác thực tài khoản không chính xác (HTTP 401 Unauthorized). */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    /** Mã lỗi khi refresh token không hợp lệ hoặc đã hết hạn (HTTP 401 Unauthorized). */
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    /** Mã lỗi khi không tìm thấy phiên làm việc (HTTP 404 Not Found). */
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

    /** Mã lỗi khi email đã được đăng ký nhưng chưa xác thực (HTTP 409 Conflict). */
    public static final String EMAIL_PENDING_VERIFICATION = "EMAIL_PENDING_VERIFICATION";

    /** Mã lỗi khi tài khoản chưa xác thực email và cố gắng đăng nhập (HTTP 403 Forbidden). */
    public static final String ACCOUNT_PENDING_VERIFICATION = "ACCOUNT_PENDING_VERIFICATION";

    /** Mã lỗi khi mã xác thực không chính xác (HTTP 400 Bad Request). */
    public static final String INVALID_VERIFICATION_CODE = "INVALID_VERIFICATION_CODE";

    /** Mã lỗi khi mã xác thực đã hết hạn (HTTP 400 Bad Request). */
    public static final String VERIFICATION_CODE_EXPIRED = "VERIFICATION_CODE_EXPIRED";

    /** Mã lỗi khi tài khoản đã được xác thực (HTTP 409 Conflict). */
    public static final String ACCOUNT_ALREADY_ACTIVE = "ACCOUNT_ALREADY_ACTIVE";

    /** Mã lỗi khi vượt quá giới hạn gửi lại mã xác thực trong một giờ (HTTP 400 Bad Request). */
    public static final String RESEND_HOURLY_LIMIT_EXCEEDED = "RESEND_HOURLY_LIMIT_EXCEEDED";

    /** Mã lỗi khi không tìm thấy tài khoản (HTTP 404 Not Found). */
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    /** Mã phản hồi thành công chung (HTTP 200 OK). */
    public static final String SUCCESS = "SUCCESS";
}
