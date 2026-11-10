package com.shareride.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu làm mới Access Token bằng Refresh Token. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cấp phát Access Token mới bằng Refresh Token hiện tại")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(
            description = "Mã Refresh Token hợp lệ được cấp trước đó",
            example = "47b864a6-79cf-4a39-ae49-1be7fa83eb3e",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
