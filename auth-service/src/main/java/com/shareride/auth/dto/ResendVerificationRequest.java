package com.shareride.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO cho việc gửi lại mã xác thực email. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendVerificationRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;
}
