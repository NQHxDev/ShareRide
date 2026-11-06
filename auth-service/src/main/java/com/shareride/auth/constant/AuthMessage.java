package com.shareride.auth.constant;

/**
 * Định nghĩa các thông điệp phản hồi mặc định (Fallback Messages) và mẫu thông báo lỗi trong Auth Service.
 * <p>
 * Đảm bảo tính nhất quán về nội dung thông báo trả về cho người dùng và ghi nhật ký hệ thống.
 * Có thể kết hợp với cơ chế đa ngôn ngữ (i18n) khi áp dụng trong ứng dụng quốc tế.
 * </p>
 *
 * <h3>Hướng dẫn sử dụng cho người tiếp quản dự án:</h3>
 * <pre>{@code
 * // Trả về thông báo thành công:
 * BaseResponse.success(AuthResponseCode.USER_REGISTERED, AuthMessage.USER_REGISTERED_SUCCESS, response);
 *
 * // Tạo thông báo lỗi xung đột email:
 * String message = AuthMessage.emailAlreadyExists("test@example.com");
 * }</pre>
 */
public final class AuthMessage {

   private AuthMessage() {
      // Ngăn chặn khởi tạo utility class
   }

   /**
    * Thông điệp thành công mặc định khi tài khoản người dùng đăng ký thành công.
    */
   public static final String USER_REGISTERED_SUCCESS = "User registered successfully";

   /**
    * Thông điệp thành công mặc định khi người dùng đăng nhập thành công.
    */
   public static final String LOGIN_SUCCESS = "Login successful";

   /**
    * Thông báo lỗi khi email hoặc mật khẩu không chính xác.
    */
   public static final String INVALID_EMAIL_OR_PASSWORD = "Invalid email or password";

   /**
    * Tiền tố thông báo trạng thái tài khoản không hoạt động (ACTIVE).
    */
   public static final String USER_ACCOUNT_STATUS_PREFIX = "User account is ";

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
    * Tạo thông báo lỗi chi tiết khi tài khoản người dùng không ở trạng thái hoạt động.
    *
    * @param status trạng thái hiện tại của tài khoản (ví dụ: SUSPENDED, PENDING_ACTIVATION)
    * @return chuỗi thông báo lỗi định dạng chuẩn
    */
   public static String userAccountInactive(Object status) {
      return USER_ACCOUNT_STATUS_PREFIX + status;
   }
}
