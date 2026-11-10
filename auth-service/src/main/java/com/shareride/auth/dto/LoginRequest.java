package com.shareride.auth.dto;

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
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    /** Định danh duy nhất của thiết bị (UUID hoặc Device Fingerprint). */
    private String deviceId;

    /** Tên thiết bị (ví dụ: iPhone 15 Pro, Chrome Browser). */
    private String deviceName;

    /** Loại thiết bị (ví dụ: MOBILE, WEB, TABLET). */
    private String deviceType;

    /** Vị trí địa lý ước lượng (ví dụ: Hanoi, Vietnam). */
    private String location;
}
