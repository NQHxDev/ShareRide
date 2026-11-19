package com.shareride.auth.domain;

/**
 * Trạng thái của thử thách xác thực email. Theo dõi vòng đời của mã xác thực từ khi tạo cho đến khi
 * hết hạn hoặc xác thực thành công.
 */
public enum VerificationStatus {

    /** Mã xác thực đang hoạt động và chờ người dùng xác thực. */
    PENDING,

    /** Mã xác thực đã được xác thực thành công và tài khoản đã được kích hoạt. */
    VERIFIED,

    /** Mã xác thực đã hết hạn (vượt quá 15 phút) hoặc bị thay thế bởi mã mới. */
    EXPIRED,

    /** Mã xác thực bị vô hiệu hóa do vượt quá 5 lần thử sai liên tiếp. */
    MAX_ATTEMPTS_EXCEEDED
}
