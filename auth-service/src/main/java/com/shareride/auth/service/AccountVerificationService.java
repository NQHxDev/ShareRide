package com.shareride.auth.service;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.ResendVerificationRequest;
import com.shareride.auth.dto.ResendVerificationResponse;
import com.shareride.auth.dto.VerifyEmailRequest;

/**
 * Service interface quản lý lifecycle xác thực email: tạo thử thách, xác thực mã OTP, gửi lại mã,
 * và kiểm tra cooldown.
 */
public interface AccountVerificationService {

    /**
     * Xác thực email bằng mã OTP 6 ký tự. Kiểm tra case-insensitive, tăng failed_attempts nếu sai,
     * và kích hoạt tài khoản thành ACTIVE nếu đúng.
     *
     * @param request chứa email và mã OTP 6 ký tự
     * @return AccountResponse với status ACTIVE sau khi xác thực thành công
     * @throws com.shareride.common.exception.ResourceNotFoundException nếu tài khoản không tồn tại
     * @throws com.shareride.common.exception.ConflictException nếu tài khoản đã ACTIVE
     * @throws com.shareride.common.exception.BadRequestException nếu mã hết hạn, sai, hoặc vượt quá
     *     số lần thử
     */
    AccountResponse verifyEmail(VerifyEmailRequest request);

    /**
     * Gửi lại mã xác thực cho tài khoản chưa xác thực. Kiểm tra cooldown 60s và giới hạn 5 lần/giờ.
     *
     * @param request chứa email cần gửi lại mã
     * @return ResendVerificationResponse với thông tin cooldown và thông báo
     * @throws com.shareride.common.exception.ResourceNotFoundException nếu tài khoản không tồn tại
     * @throws com.shareride.common.exception.ConflictException nếu tài khoản đã ACTIVE
     * @throws com.shareride.common.exception.RateLimitExceededException nếu trong cooldown 60s
     * @throws com.shareride.common.exception.BadRequestException nếu vượt quá 5 lần/giờ
     */
    ResendVerificationResponse resendVerification(ResendVerificationRequest request);
}
