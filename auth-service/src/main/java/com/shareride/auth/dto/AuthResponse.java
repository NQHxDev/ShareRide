package com.shareride.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi xác thực thành công chứa cặp Access Token, Refresh Token, Session ID và thông tin
 * tài khoản.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Kết quả xác thực thành công gồm cặp JWT Tokens, Session ID và thông tin tài khoản")
public class AuthResponse {

    @Schema(
            description =
                    "Asymmetric EdDSA (Ed25519) Access Token JWT dùng để xác thực các API sau",
            example =
                    "eyJhbGciOiJFZERTQSIsInR5cCI6ImF0K2p3dCIsImtpZCI6InNoYXJlcmlkZS1hdXRoLWtleS0xIn0...")
    private String accessToken;

    @Schema(
            description = "Refresh Token dạng opaque UUIDv4 lưu trong Valkey dùng để đổi token mới",
            example = "d1b033aa-448f-4ba3-a442-5cb0098dfca1")
    private String refreshToken;

    @Schema(description = "Loại mã định danh bảo mật (luôn là Bearer)", example = "Bearer")
    private String tokenType;

    @Schema(
            description = "Thời điểm Access Token hết hạn theo chuẩn UTC ISO-8601",
            example = "2026-11-10T12:15:00.000Z")
    private Instant expiresIn;

    @Schema(
            description = "UUID của phiên đăng nhập hiện tại trên thiết bị",
            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
    private UUID sessionId;

    @Schema(description = "Chi tiết hồ sơ và quyền hạn tài khoản")
    private AccountResponse account;
}
