package com.shareride.auth.constant;

/** Định nghĩa các hằng số dùng cho mẫu thiết kế Transactional Outbox Pattern trong Auth Service. */
public final class AuthOutboxConstants {

    private AuthOutboxConstants() {
        // Ngăn chặn khởi tạo utility class
    }

    /** Tên phân loại Aggregate Root liên quan tới tài khoản trong hệ thống ShareRide. */
    public static final String AGGREGATE_TYPE_ACCOUNT = "Account";

    /**
     * Danh mục các loại sự kiện nghiệp vụ (Domain Event Types) xuất bản qua Transactional Outbox.
     */
    public static final class EventType {
        private EventType() {}

        /**
         * Bắn ra khi tài khoản đăng ký thành công để các service vệ tinh (User Service, Wallet
         * Service...) khởi tạo dữ liệu hồ sơ và ví.
         */
        public static final String ACCOUNT_REGISTERED = "ACCOUNT_REGISTERED";

        /** Bắn ra khi tài khoản bị xóa mềm (Soft Delete / Deactivation). */
        public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    }

    /**
     * Các khóa thuộc tính (Payload Property Keys) trong đối tượng JSON lưu vào bảng {@code
     * outbox_messages}.
     */
    public static final class PayloadKey {
        private PayloadKey() {}

        /** Khóa định danh UUID duy nhất của tài khoản: {@code "accountId"}. */
        public static final String ACCOUNT_ID = "accountId";

        /** Khóa chứa địa chỉ email: {@code "email"}. */
        public static final String EMAIL = "email";

        /** Khóa chứa số điện thoại: {@code "phoneNumber"}. */
        public static final String PHONE_NUMBER = "phoneNumber";

        /**
         * Khóa chứa họ tên đầy đủ người dùng (chuyển tiếp sang User Service): {@code "fullName"}.
         */
        public static final String FULL_NAME = "fullName";

        /** Khóa chứa danh sách vai trò tài khoản: {@code "roles"}. */
        public static final String ROLES = "roles";
    }
}
