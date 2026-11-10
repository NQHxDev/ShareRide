package com.shareride.auth.service;

import com.shareride.auth.dto.RefreshTokenData;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Service quản lý lưu trữ, truy xuất và thu hồi Refresh Token trên Valkey (Redis). */
public interface RefreshTokenCacheService {

    /**
     * Lưu trữ Refresh Token vào Valkey gắn với session và account.
     *
     * @param sessionId ID của phiên đăng nhập
     * @param accountId ID của tài khoản
     * @param refreshToken chuỗi refresh token dạng raw
     * @param ttl thời gian sống của token
     */
    void saveRefreshToken(UUID sessionId, UUID accountId, String refreshToken, Duration ttl);

    /**
     * Tìm kiếm và kiểm tra dữ liệu của Refresh Token từ Valkey.
     *
     * @param refreshToken chuỗi refresh token dạng raw
     * @return đối tượng RefreshTokenData nếu tồn tại và hợp lệ
     */
    Optional<RefreshTokenData> getRefreshTokenData(String refreshToken);

    /**
     * Thu hồi Refresh Token khỏi Valkey khi người dùng chủ động đăng xuất hoặc refresh.
     *
     * @param refreshToken chuỗi refresh token cần thu hồi
     */
    void revokeRefreshToken(String refreshToken);

    /**
     * Thu hồi toàn bộ token gắn liền với một SessionId cụ thể trên Valkey.
     *
     * @param sessionId ID của phiên đăng nhập
     * @param accountId ID của tài khoản
     */
    void revokeSession(UUID sessionId, UUID accountId);

    /**
     * Thu hồi tất cả các phiên đăng nhập của một tài khoản trên Valkey.
     *
     * @param accountId ID của tài khoản
     */
    void revokeAllSessions(UUID accountId);
}
