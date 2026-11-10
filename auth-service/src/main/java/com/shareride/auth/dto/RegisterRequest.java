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
public class RegisterRequest {

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

    @NotBlank(message = "Phone number is required")
    @Schema(
            description = "Số điện thoại liên lạc chuẩn E.164 hoặc quốc gia",
            example = "+84901234567",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNumber;

    /** Tên đầy đủ (tùy chọn, được chuyển tiếp qua Outbox event sang User Service để tạo hồ sơ). */
    @Schema(
            description = "Họ và tên đầy đủ của người dùng (sẽ gửi Outbox Event sang User Service)",
            example = "Nguyen Van A")
    private String fullName;

    /** Mã vai trò mong muốn (mặc định: ROLE_RIDER). */
    @Schema(
            description = "Mã vai trò RBAC khởi tạo cho tài khoản (mặc định: ROLE_RIDER)",
            example = "ROLE_RIDER",
            defaultValue = "ROLE_RIDER")
    private String roleCode;
}
