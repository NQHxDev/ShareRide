package com.shareride.auth.constant;

/**
 * Định nghĩa tập trung mã phản hồi nghiệp vụ (Business Response Codes) và mã lỗi cho Auth Service.
 *
 * <p>Các mã này được đóng gói trong thuộc tính {@code code} của {@link
 * com.shareride.common.response.BaseResponse} nhằm giúp Frontend/Client xác định chính xác ngữ cảnh
 * phản hồi thay vì chỉ dựa vào mã trạng thái HTTP.
 *
 * <h3>Hướng dẫn sử dụng cho người tiếp quản dự án:</h3>
 *
 * <pre>{@code
 * // 1. Trả về phản hồi thành công trong Controller:
 * return ResponseEntity.ok(BaseResponse.success(AuthResponseCode.LOGIN_SUCCESS, AuthMessage.LOGIN_SUCCESS, data));
 *
 * // 2. Ném lỗi nghiệp vụ trong Service:
 * throw new ConflictException(AuthResponseCode.EMAIL_ALREADY_EXISTS, AuthMessage.emailAlreadyExists(email));
 * }</pre>
 */
public final class AuthResponseCode {

    private AuthResponseCode() {
        // Ngăn chặn khởi tạo utility class
    }

    /** Mã phản hồi khi tài khoản người dùng được tạo mới thành công (HTTP 201 Created). */
    public static final String USER_REGISTERED = "USER_REGISTERED";

    /** Mã phản hồi khi người dùng xác thực và đăng nhập thành công (HTTP 200 OK). */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

    /** Mã lỗi khi địa chỉ email đăng ký đã tồn tại trong hệ thống (HTTP 409 Conflict). */
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";

    /** Mã lỗi khi số điện thoại đăng ký đã tồn tại trong hệ thống (HTTP 409 Conflict). */
    public static final String PHONE_ALREADY_EXISTS = "PHONE_ALREADY_EXISTS";

    /** Mã lỗi khi thông tin xác thực tài khoản không chính xác (HTTP 401 Unauthorized). */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
}
