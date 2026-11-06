package com.shareride.auth.constant;

/**
 * Định nghĩa các hằng số dùng cho mẫu thiết kế Transactional Outbox Pattern trong Auth Service.
 * <p>
 * Đảm bảo tính nhất quán dữ liệu (Dual-write problem / At-Least-Once Delivery) khi xuất bản
 * Domain Events sang các microservices khác (Driver Service, Notification Service, Trip Service...).
 * </p>
 *
 * <h3>Hướng dẫn sử dụng cho người tiếp quản dự án:</h3>
 * <pre>{@code
 * // Xuất bản sự kiện Outbox khi người dùng đăng ký tài khoản thành công:
 * outboxService.recordEvent(
 *       AuthOutboxConstants.AGGREGATE_TYPE_USER,
 *       savedUser.getId().toString(),
 *       AuthOutboxConstants.EventType.USER_REGISTERED,
 *       Map.of(
 *             AuthOutboxConstants.PayloadKey.USER_ID, savedUser.getId(),
 *             AuthOutboxConstants.PayloadKey.EMAIL, savedUser.getEmail(),
 *             AuthOutboxConstants.PayloadKey.FULL_NAME, savedUser.getFullName(),
 *             AuthOutboxConstants.PayloadKey.ROLE, savedUser.getRole().name()
 *       )
 * );
 * }</pre>
 */
public final class AuthOutboxConstants {

   private AuthOutboxConstants() {
      // Ngăn chặn khởi tạo utility class
   }

   /**
    * Tên phân loại Aggregate Root liên quan tới tài khoản người dùng trong hệ thống ShareRide.
    */
   public static final String AGGREGATE_TYPE_USER = "User";

   /**
    * Danh mục các loại sự kiện nghiệp vụ (Domain Event Types) xuất bản qua Transactional Outbox.
    */
   public static final class EventType {
      private EventType() {
         // Ngăn chặn khởi tạo utility class
      }

      /**
       * Bắn ra khi người dùng đăng ký tài khoản thành công để các service vệ tinh khởi tạo dữ liệu profile/wallet.
       */
      public static final String USER_REGISTERED = "USER_REGISTERED";

      /**
       * Bắn ra khi tài khoản người dùng bị xóa mềm (Soft Delete / Account Deactivation).
       */
      public static final String USER_DELETED = "USER_DELETED";
   }

   /**
    * Các khóa thuộc tính (Payload Property Keys) trong đối tượng JSON lưu vào bảng {@code outbox_messages}.
    */
   public static final class PayloadKey {
      private PayloadKey() {
         // Ngăn chặn khởi tạo utility class
      }

      /**
       * Khóa định danh UUID duy nhất của người dùng: {@code "userId"}.
       */
      public static final String USER_ID = "userId";

      /**
       * Khóa chứa địa chỉ email người dùng: {@code "email"}.
       */
      public static final String EMAIL = "email";

      /**
       * Khóa chứa họ tên đầy đủ người dùng: {@code "fullName"}.
       */
      public static final String FULL_NAME = "fullName";

      /**
       * Khóa chứa vai trò tài khoản người dùng (RIDER, DRIVER, ADMIN...): {@code "role"}.
       */
      public static final String ROLE = "role";
   }
}
