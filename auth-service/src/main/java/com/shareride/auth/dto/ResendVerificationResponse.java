package com.shareride.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO cho việc gửi lại mã xác thực, bao gồm cooldown và thông báo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendVerificationResponse {

    private String email;

    private int cooldownSeconds;

    private String message;
}
