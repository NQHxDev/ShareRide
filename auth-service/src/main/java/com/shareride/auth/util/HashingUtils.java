package com.shareride.auth.util;

import com.shareride.auth.constant.AuthConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Tiện ích băm mật mã với SHA-256 và so sánh chuỗi theo thời gian không đổi. Hỗ trợ chuẩn hóa chữ
 * hoa và định dạng hex thường.
 */
public final class HashingUtils {

    private HashingUtils() {
        // Ngăn chặn khởi tạo utility class
    }

    /**
     * Tạo hash SHA-256 dưới dạng chuỗi hex thường từ chuỗi đầu vào. Chuỗi đầu vào được trim và
     * chuẩn hóa thành chữ hoa trước khi băm.
     *
     * @param input chuỗi cần băm
     * @return chuỗi hex thường 64 ký tự biểu diễn hash SHA-256
     */
    public static String sha256Hex(final String input) {
        try {
            final String normalized = input.trim().toUpperCase(Locale.ROOT);
            final MessageDigest digest =
                    MessageDigest.getInstance(AuthConstants.HASH_ALGORITHM_SHA256);
            final byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * So sánh hai chuỗi hash theo thời gian không đổi để ngăn chặn timing attack.
     *
     * @param hash1 chuỗi hash thứ nhất
     * @param hash2 chuỗi hash thứ hai
     * @return true nếu hai hash bằng nhau
     */
    public static boolean constantTimeEquals(final String hash1, final String hash2) {
        if (hash1 == null || hash2 == null) {
            return false;
        }
        final byte[] bytes1 = hash1.getBytes(StandardCharsets.UTF_8);
        final byte[] bytes2 = hash2.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(bytes1, bytes2);
    }

    private static String bytesToHex(final byte[] bytes) {
        final StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
