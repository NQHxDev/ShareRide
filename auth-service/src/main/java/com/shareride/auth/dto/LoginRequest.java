package com.shareride.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu xác thực đăng nhập tài khoản kèm thông tin thiết bị. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin đăng nhập tài khoản và metadata thiết bị khách")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(
            description = "Địa chỉ email đã đăng ký của tài khoản",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(
            description = "Mật khẩu tài khoản",
            example = "SecurePass123!@#",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    /** Định danh duy nhất của thiết bị (UUID hoặc Device Fingerprint). */
    @Schema(
            description = "Định danh thiết bị duy nhất hoặc device fingerprint",
            example = "d8e3c540-1e56-4993-96b4-ff73df7819ad")
    private String deviceId;

    /** Tên thiết bị (ví dụ: iPhone 15 Pro, Chrome Browser). */
    @Schema(description = "Tên hiển thị của thiết bị người dùng", example = "iPhone 15 Pro")
    private String deviceName;

    /** Loại thiết bị (ví dụ: MOBILE, WEB, TABLET). */
    @Schema(description = "Loại nền tảng thiết bị (MOBILE, WEB, TABLET)", example = "MOBILE")
    private String deviceType;

    /** Vị trí địa lý ước lượng (ví dụ: Hanoi, Vietnam). */
    @Schema(description = "Vị trí địa lý đăng nhập ước lượng", example = "Hanoi, Vietnam")
    private String location;
}
