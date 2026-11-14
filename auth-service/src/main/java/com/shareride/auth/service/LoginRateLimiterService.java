package com.shareride.auth.service;

/**
 * Service giao diện quản lý giới hạn tần suất đăng nhập và chống tấn công Brute-force trong Auth
 * Service.
 *
 * <p>Quy chuẩn kiểm soát:
 *
 * <ul>
 *   <li>Giới hạn IP-based: Tối đa 5 lần thử sai trong 15 phút.
 *   <li>Giới hạn Account-based: Tối đa 10 lần thử sai trong 1 giờ; tự động khóa tài khoản khi vượt
 *       ngưỡng.
 * </ul>
 */
public interface LoginRateLimiterService {

    /**
     * Kiểm tra xem địa chỉ IP hiện tại có đang bị chặn do vượt quá 5 lần đăng nhập sai trong 15
     * phút hay không.
     *
     * @param ipAddress địa chỉ IP của client gửi request
     * @return {@code true} nếu IP đang bị chặn rate limit, ngược lại {@code false}
     */
    boolean isIpBlocked(String ipAddress);

    /**
     * Kiểm tra xem tài khoản (email) có vượt quá ngưỡng 10 lần thử sai trong 1 giờ hay không.
     *
     * @param email địa chỉ email của tài khoản
     * @return {@code true} nếu tài khoản đã vượt ngưỡng thử sai, ngược lại {@code false}
     */
    boolean isAccountRateLimitExceeded(String email);

    /**
     * Ghi nhận một lần đăng nhập thất bại cho cả địa chỉ IP và tài khoản (nếu có email).
     *
     * @param ipAddress địa chỉ IP của client
     * @param email địa chỉ email gửi lên trong request đăng nhập (có thể null hoặc không hợp lệ)
     * @return số lần thử sai hiện tại của tài khoản trong cửa sổ 1 giờ
     */
    int recordLoginFailure(String ipAddress, String email);

    /**
     * Xóa bỏ lịch sử các lần thử sai của cả địa chỉ IP và tài khoản khi đăng nhập thành công.
     *
     * @param ipAddress địa chỉ IP của client
     * @param email địa chỉ email của tài khoản
     */
    void resetLoginAttempts(String ipAddress, String email);

    /**
     * Lấy số lần thử đăng nhập thất bại hiện tại của một địa chỉ IP.
     *
     * @param ipAddress địa chỉ IP của client
     * @return số lần thử sai hiện tại
     */
    int getIpFailedAttempts(String ipAddress);

    /**
     * Lấy số lần thử đăng nhập thất bại hiện tại của một tài khoản.
     *
     * @param email địa chỉ email của tài khoản
     * @return số lần thử sai hiện tại
     */
    int getAccountFailedAttempts(String email);
}
