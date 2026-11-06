package com.shareride.auth.constant;

/**
 * Định nghĩa tập trung các hằng số liên quan đến Security Audit Log trong phân hệ Auth Service.
 * <p>
 * Hệ thống kiểm toán an ninh (Security Audit Log) được dùng để truy vết các sự kiện liên quan
 * đến định danh người dùng, đăng ký, đăng nhập và bảo mật phiên theo chuẩn SIEM/SOC.
 * </p>
 *
 * <h3>Hướng dẫn sử dụng cho người tiếp quản dự án:</h3>
 * <pre>{@code
 * // Ví dụ ghi audit log khi người dùng đăng nhập thành công:
 * auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
 *       .eventType(AuthSecurityAuditConstants.EventType.LOGIN_SUCCESS)
 *       .userId(user.getId().toString())
 *       .resource(AuthConstants.AuditResource.LOGIN)
 *       .status(AuthSecurityAuditConstants.Status.SUCCESS)
 *       .build());
 *
 * // Ví dụ ghi audit log khi đăng nhập thất bại do sai mật khẩu:
 * auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
 *       .eventType(AuthSecurityAuditConstants.EventType.LOGIN_FAILURE)
 *       .userId(user.getId().toString())
 *       .resource(AuthConstants.AuditResource.LOGIN)
 *       .status(AuthSecurityAuditConstants.Status.INVALID_CREDENTIALS)
 *       .build());
 * }</pre>
 */
public final class AuthSecurityAuditConstants {

   private AuthSecurityAuditConstants() {
      // Ngăn chặn khởi tạo utility class
   }

   /**
    * Các loại sự kiện an toàn thông tin (Security Audit Event Types).
    */
   public static final class EventType {
      private EventType() {
         // Ngăn chặn khởi tạo utility class
      }

      /**
       * Ghi nhận sự kiện người dùng đăng ký tài khoản mới thành công vào hệ thống.
       */
      public static final String USER_REGISTRATION = "USER_REGISTRATION";

      /**
       * Ghi nhận sự kiện người dùng đăng nhập thành công với thông tin xác thực hợp lệ.
       */
      public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

      /**
       * Ghi nhận sự kiện người dùng đăng nhập thất bại (sai mật khẩu, tài khoản không tồn tại, bị khóa...).
       */
      public static final String LOGIN_FAILURE = "LOGIN_FAILURE";

      /**
       * Ghi nhận sự kiện người dùng đăng xuất và thu hồi token/phiên làm việc (Revoke token).
       */
      public static final String LOGOUT = "LOGOUT";
   }

   /**
    * Trạng thái kết quả của sự kiện bảo mật (Security Audit Status).
    */
   public static final class Status {
      private Status() {
         // Ngăn chặn khởi tạo utility class
      }

      /**
       * Hành vi xác thực hoặc kiểm toán bảo mật hoàn thành thành công.
       */
      public static final String SUCCESS = "SUCCESS";

      /**
       * Thất bại do không tìm thấy tài khoản người dùng trong cơ sở dữ liệu.
       */
      public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

      /**
       * Thất bại do thông tin xác thực không hợp lệ (sai mật khẩu hoặc token không khớp).
       */
      public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
   }
}
