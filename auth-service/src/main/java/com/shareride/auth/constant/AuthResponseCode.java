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
}
