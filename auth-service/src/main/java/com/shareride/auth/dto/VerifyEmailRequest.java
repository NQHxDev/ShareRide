package com.shareride.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO cho xác thực email với mã OTP 6 ký tự alphanumeric. Hỗ trợ case-insensitive
 * validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mã xác thực không được để trống")
    @Size(min = 6, max = 6, message = "Mã xác thực phải có đúng 6 ký tự")
    private String code;
}
