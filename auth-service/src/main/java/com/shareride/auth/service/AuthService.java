package com.shareride.auth.service;

import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.UserResponse;
import java.util.UUID;

/**
 * Interface nghiệp vụ xác thực người dùng, phân quyền và quản lý tài khoản trong Auth Service theo
 * chuẩn Enterprise.
 */
public interface AuthService {

    /**
     * Đăng ký tài khoản người dùng mới.
     *
     * @param request thông tin đăng ký
     * @return thông tin người dùng được tạo
     */
    UserResponse register(RegisterRequest request);

    /**
     * Xác thực thông tin đăng nhập và cấp phát token.
     *
     * @param request thông tin đăng nhập
     * @return thông tin xác thực chứa access token và refresh token
     */
    AuthResponse login(LoginRequest request);

    /**
     * Đăng xuất người dùng, đưa token vào blacklist và thu hồi refresh token.
     *
     * @param accessToken mã access token
     * @param refreshTokenString mã refresh token
     */
    void logout(String accessToken, String refreshTokenString);

    /**
     * Tra cứu chi tiết tài khoản người dùng theo UUID.
     *
     * @param userId UUID người dùng
     * @return thông tin chi tiết người dùng
     */
    UserResponse getUserById(UUID userId);

    /**
     * Xóa mềm tài khoản người dùng và xuất bản Domain Event sang Transactional Outbox.
     *
     * @param userId UUID người dùng
     * @param deletedBy định danh người thực hiện xóa
     */
    void softDeleteUser(UUID userId, String deletedBy);
}
