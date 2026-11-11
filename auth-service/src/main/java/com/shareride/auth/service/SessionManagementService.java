package com.shareride.auth.service;

import com.shareride.auth.domain.Session;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.SessionResponse;
import java.util.List;
import java.util.UUID;

/** Service quản lý vòng đời và phiên làm việc (Session) của tài khoản trong Auth Service. */
public interface SessionManagementService {

    /**
     * Khởi tạo phiên làm việc mới khi đăng nhập thành công trên thiết bị.
     *
     * @param accountId ID của tài khoản đăng nhập
     * @param request dữ liệu đăng nhập chứa metadata thiết bị
     * @param ipAddress địa chỉ IP của client
     * @param userAgent User-Agent của client
     * @return thực thể phiên làm việc vừa tạo
     */
    Session createSession(UUID accountId, LoginRequest request, String ipAddress, String userAgent);

    /**
     * Xác thực và làm mới phiên làm việc khi thực hiện xoay vòng Refresh Token (Token Rotation).
     *
     * @param refreshToken chuỗi Refresh Token hiện tại
     * @param ipAddress địa chỉ IP mới của client (nếu có)
     * @return thực thể phiên làm việc đã được cập nhật
     */
    Session refreshSession(String refreshToken, String ipAddress);

    /**
     * Kết thúc phiên làm việc khi người dùng đăng xuất, thu hồi Refresh Token trên Valkey.
     *
     * @param refreshToken chuỗi Refresh Token cần thu hồi
     */
    void terminateSession(String refreshToken);

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
     * Thu hồi toàn bộ các phiên làm việc của một tài khoản trên DB và Valkey.
     *
     * @param accountId ID của tài khoản
     */
    void revokeAllSessions(UUID accountId);

    /**
     * Chuyển đổi đối tượng thực thể {@link Session} sang DTO {@link SessionResponse}.
     *
     * @param session thực thể phiên làm việc
     * @return DTO biểu diễn phiên làm việc
     */
    SessionResponse toSessionResponse(Session session);
}
