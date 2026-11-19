package com.shareride.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Yêu cầu đăng ký tài khoản xác thực người dùng mới")
public class RegisterRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(
            description = "Địa chỉ email duy nhất của tài khoản",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(
            description = "Mật khẩu xác thực (tối thiểu 8 ký tự)",
            example = "SecurePass123!@#",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "Confirm password is required")
    @Schema(
            description = "Xác nhận mật khẩu (phải khớp với password)",
            example = "SecurePass123!@#",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmPassword;
}
