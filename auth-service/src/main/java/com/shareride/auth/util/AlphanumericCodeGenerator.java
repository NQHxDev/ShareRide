package com.shareride.auth.util;

import com.shareride.auth.constant.AuthVerificationConstants;
import java.security.SecureRandom;

/**
 * Trình tạo mã xác thực chữ và số không nhầm lẫn. Sử dụng SecureRandom để tạo chuỗi 6 ký tự từ bộ
 * 32 ký tự loại trừ 0, O, 1, I để tránh nhầm lẫn khi nhập liệu.
 */
public final class AlphanumericCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AlphanumericCodeGenerator() {
        // Ngăn chặn khởi tạo utility class
    }

    /**
     * Tạo mã xác thực chữ và số ngẫu nhiên gồm 6 ký tự từ bộ ký tự không nhầm lẫn.
     *
     * @return chuỗi 6 ký tự chữ hoa và chữ số
     */
    public static String generate() {
        final StringBuilder code = new StringBuilder(AuthVerificationConstants.OTP_LENGTH);
        final String chars = AuthVerificationConstants.UNAMBIGUOUS_CHARS;
        final int charSetLength = chars.length();

        for (int i = 0; i < AuthVerificationConstants.OTP_LENGTH; i++) {
            final int index = SECURE_RANDOM.nextInt(charSetLength);
            code.append(chars.charAt(index));
        }

        return code.toString();
    }
}
