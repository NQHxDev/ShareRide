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

    /** Thông báo khi email đã được đăng ký nhưng chưa xác thực. */
    public static final String EMAIL_PENDING_VERIFICATION =
            "Email này đã được đăng ký nhưng chưa xác thực. Vui lòng kiểm tra hộp thư và xác thực"
                    + " tài khoản";

    /** Thông báo khi tài khoản chưa xác thực email và cố gắng đăng nhập. */
    public static final String ACCOUNT_PENDING_VERIFICATION =
            "Tài khoản chưa được xác thực. Vui lòng kiểm tra email để xác thực tài khoản";

    /** Thông báo khi mã xác thực không chính xác. */
    public static final String INVALID_VERIFICATION_CODE =
            "Mã xác thực không chính xác. Vui lòng kiểm tra lại";

    /** Thông báo khi mã xác thực đã hết hạn. */
    public static final String VERIFICATION_CODE_EXPIRED =
            "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới";

    /** Thông báo khi tài khoản đã được xác thực. */
    public static final String ACCOUNT_ALREADY_ACTIVE = "Tài khoản đã được xác thực";

    /** Thông báo khi vượt quá giới hạn gửi lại mã xác thực trong một giờ. */
    public static final String RESEND_HOURLY_LIMIT_EXCEEDED =
            "Bạn đã vượt quá số lần gửi lại cho phép trong một giờ. Vui lòng thử lại sau";

    /** Thông báo thành công khi gửi lại mã xác thực. */
    public static final String VERIFICATION_CODE_RESENT =
            "Mã xác thực mới đã được gửi tới email của bạn";

    /** Thông báo thành công khi xác thực email thành công. */
    public static final String EMAIL_VERIFIED_SUCCESS = "Email đã được xác thực thành công";

    /** Thông báo lỗi khi không tìm thấy tài khoản. */
    public static final String ACCOUNT_NOT_FOUND = "Không tìm thấy tài khoản";
}
