package com.shareride.auth.service;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequest;

/**
 * Service xử lý quy trình đăng ký tài khoản xác thực mới, phân quyền RBAC và phát sinh sự kiện
 * Outbox.
 */
public interface RegistrationService {

    /**
     * Đăng ký tài khoản xác thực mới.
     *
     * @param request dữ liệu đăng ký chứa email, password, phoneNumber, roleCode...
     * @return chi tiết tài khoản vừa tạo
     */
    AccountResponse register(RegisterRequest request);
}
