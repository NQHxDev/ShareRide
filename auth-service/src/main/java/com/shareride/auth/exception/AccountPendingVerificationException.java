package com.shareride.auth.exception;

import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.common.exception.ForbiddenException;
import lombok.Getter;

/**
 * Exception ném ra khi tài khoản chưa xác thực email và cố gắng đăng nhập với thông tin đăng nhập
 * hợp lệ. Ngoại lệ này mang theo metadata bao gồm email và số giây cooldown còn lại để gửi lại mã
 * xác thực.
 */
@Getter
public class AccountPendingVerificationException extends ForbiddenException {

    private final String email;
    private final int remainingCooldownSeconds;
    private final String responseCode;

    /**
     * Khởi tạo exception với email và số giây cooldown còn lại.
     *
     * @param email email của tài khoản chưa xác thực
     * @param remainingCooldownSeconds số giây còn lại trước khi có thể gửi lại mã xác thực (0 nếu
     *     không có cooldown)
     */
    public AccountPendingVerificationException(String email, int remainingCooldownSeconds) {
        super(AuthMessage.ACCOUNT_PENDING_VERIFICATION);
        this.email = email;
        this.remainingCooldownSeconds = Math.max(0, remainingCooldownSeconds);
        this.responseCode = AuthResponseCode.ACCOUNT_PENDING_VERIFICATION;
    }
}
