package com.shareride.auth.service;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.SessionResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service giao diện xử lý các nghiệp vụ xác thực, quản lý tài khoản và phiên làm việc trong Auth
 * Service.
 */
public interface AuthService {

    /**
     * Đăng ký tài khoản xác thực mới.
     *
     * @param request dữ liệu đăng ký chứa email, password, phoneNumber, roleCode...
     * @return chi tiết tài khoản vừa tạo
     */
    AccountResponse register(RegisterRequest request);

    /**
     * Xác thực thông tin đăng nhập và tạo phiên làm việc mới trên thiết bị.
     *
     * @param request thông tin đăng nhập gồm email, mật khẩu và metadata thiết bị
     * @param ipAddress địa chỉ IP của client gửi request
     * @param userAgent thông tin User-Agent của client
     * @return cặp Access Token, Refresh Token, Session ID và thông tin tài khoản
     */
    AuthResponse login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * Làm mới Access Token và Refresh Token (Rotation) từ Refresh Token hiện tại.
     *
     * @param refreshToken chuỗi refresh token hiện tại
     * @param ipAddress địa chỉ IP của client gửi request
     * @param userAgent thông tin User-Agent của client
     * @return cặp Access Token và Refresh Token mới
     */
    AuthResponse refreshToken(String refreshToken, String ipAddress, String userAgent);

    /**
     * Đăng xuất phiên làm việc, đưa Access Token vào Blacklist và xóa Refresh Token khỏi Valkey.
     *
     * @param accessToken Access Token cần đưa vào Blacklist
     * @param refreshToken Refresh Token cần thu hồi
     */
    void logout(String accessToken, String refreshToken);

    /**
     * Thu hồi một phiên làm việc cụ thể của tài khoản (Logout thiết bị từ xa).
     *
     * @param sessionId ID phiên làm việc cần thu hồi
     * @param currentAccountId ID tài khoản yêu cầu thu hồi
     */
    void revokeSession(UUID sessionId, UUID currentAccountId);

    /**
     * Lấy danh sách các phiên làm việc đang hoạt động của tài khoản.
     *
     * @param accountId ID tài khoản cần truy vấn
     * @return danh sách các phiên đăng nhập đang hoạt động
     */
    List<SessionResponse> getActiveSessions(UUID accountId);

    /**
     * Lấy thông tin tài khoản theo Account ID.
     *
     * @param accountId ID của tài khoản
     * @return chi tiết tài khoản và vai trò RBAC
     */
    AccountResponse getAccountById(UUID accountId);

    /**
     * Xóa mềm tài khoản và hủy toàn bộ phiên làm việc của tài khoản.
     *
     * @param accountId ID của tài khoản
     * @param deletedBy người thực hiện thao tác xóa
     */
    void softDeleteAccount(UUID accountId, String deletedBy);

    /**
     * Lấy tập khóa công khai JWKS (RFC 7517) để Resource Server / API Gateway xác thực chữ ký số
     * EdDSA.
     *
     * @return Bản đồ cấu trúc JSON của JWKS theo chuẩn RFC 7517
     */
    Map<String, Object> getJwks();
}
