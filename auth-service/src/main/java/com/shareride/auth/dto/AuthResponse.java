package com.shareride.auth.dto;

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
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Instant expiresIn;
    private UUID sessionId;
    private AccountResponse account;
}
