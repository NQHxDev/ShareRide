package com.shareride.auth.constant;

/** Định nghĩa các thông điệp phản hồi mặc định và mẫu thông báo lỗi trong Auth Service. */
public final class AuthMessage {

    private AuthMessage() {
        // Ngăn chặn khởi tạo utility class
    }

    /** Thông điệp thành công mặc định khi tài khoản đăng ký thành công. */
    public static final String ACCOUNT_REGISTERED_SUCCESS = "Account registered successfully";

    /** Thông điệp thành công mặc định khi đăng nhập thành công. */
    public static final String LOGIN_SUCCESS = "Login successful";

    /** Thông điệp thành công khi refresh token thành công. */
    public static final String REFRESH_SUCCESS = "Token refreshed successfully";

    /** Thông điệp thành công khi thu hồi phiên làm việc. */
    public static final String SESSION_REVOKED_SUCCESS = "Session revoked successfully";

    /** Thông báo lỗi khi email hoặc mật khẩu không chính xác. */
    public static final String INVALID_EMAIL_OR_PASSWORD = "Invalid email or password";

    /** Thông báo lỗi khi refresh token không hợp lệ hoặc đã bị thu hồi/hết hạn. */
    public static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";

    /** Thông báo lỗi khi session đã bị thu hồi hoặc hết hạn. */
    public static final String SESSION_EXPIRED_OR_REVOKED =
            "Session is expired or has been revoked";

    /** Tiền tố thông báo trạng thái tài khoản không hoạt động. */
    public static final String ACCOUNT_STATUS_PREFIX = "Account is ";

    /** Thông báo lỗi khi IP vượt quá số lần thử đăng nhập cho phép (5 lần / 15 phút). */
    public static final String IP_RATE_LIMIT_EXCEEDED =
            "Too many failed login attempts from this IP address. Please try again after 15"
                    + " minutes";

    /** Thông báo lỗi khi tài khoản bị khóa do vượt quá số lần thử sai (10 lần / 1 giờ). */
    public static final String ACCOUNT_LOCKED_TOO_MANY_ATTEMPTS =
            "Account has been locked due to too many failed login attempts";

    /** Thông báo lỗi khi vượt quá giới hạn tần suất yêu cầu Resilience4j tới Login endpoint. */
    public static final String TOO_MANY_LOGIN_REQUESTS =
            "Too many login requests. Please try again later";

    /**
     * Tạo thông báo lỗi chi tiết khi địa chỉ email đã tồn tại.
     *
     * @param email địa chỉ email bị trùng
     * @return chuỗi thông báo lỗi định dạng chuẩn
     */
    public static String emailAlreadyExists(String email) {
        return "Email is already registered: " + email;
    }

    /**
     * Tạo thông báo lỗi chi tiết khi số điện thoại đã tồn tại.
     *
     * @param phoneNumber số điện thoại bị trùng
     * @return chuỗi thông báo lỗi định dạng chuẩn
     */
    public static String phoneNumberAlreadyExists(String phoneNumber) {
        return "Phone number is already registered: " + phoneNumber;
    }

    /**
     * Tạo thông báo lỗi chi tiết khi tài khoản không ở trạng thái hoạt động.
     *
     * @param status trạng thái hiện tại của tài khoản
     * @return chuỗi thông báo lỗi định dạng chuẩn
     */
    public static String accountInactive(Object status) {
        return ACCOUNT_STATUS_PREFIX + status;
    }
}
