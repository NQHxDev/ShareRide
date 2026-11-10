package com.shareride.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu đăng ký tài khoản xác thực mới trong Auth Service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    /** Tên đầy đủ (tùy chọn, được chuyển tiếp qua Outbox event sang User Service để tạo hồ sơ). */
    private String fullName;

    /** Mã vai trò mong muốn (mặc định: ROLE_RIDER). */
    private String roleCode;
}
