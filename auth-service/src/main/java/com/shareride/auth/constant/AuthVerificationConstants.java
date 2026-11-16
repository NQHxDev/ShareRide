package com.shareride.auth.constant;

import java.time.Duration;

/**
 * Tập hợp các hằng số cấu hình cho luồng xác thực email bất đồng bộ: bộ ký tự không nhầm lẫn, độ
 * dài OTP, thời gian hết hạn, ngưỡng tần suất gửi lại, và giới hạn thử sai.
 */
public final class AuthVerificationConstants {

    private AuthVerificationConstants() {
        // Ngăn chặn khởi tạo utility class
    }

    /**
     * Bộ ký tự không nhầm lẫn gồm 32 ký tự chữ số và chữ cái hoa: loại trừ 0, O, 1, I để tránh nhầm
     * lẫn trên màn hình di động và khi nhập liệu.
     */
    public static final String UNAMBIGUOUS_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** Độ dài chuỗi mã xác thực OTP (6 ký tự). */
    public static final int OTP_LENGTH = 6;

    /** Thời gian hết hạn của mã xác thực (15 phút kể từ khi tạo). */
    public static final Duration EXPIRY_DURATION = Duration.ofMinutes(15);

    /** Thời gian chờ tối thiểu giữa hai lần yêu cầu gửi lại mã xác thực (60 giây). */
    public static final Duration COOLDOWN_DURATION = Duration.ofSeconds(60);

    /** Số lần thử sai tối đa trước khi mã xác thực bị vô hiệu hóa (5 lần). */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Số lần gửi lại tối đa trong một giờ (5 lần). */
    public static final int HOURLY_RESEND_LIMIT = 5;
}
