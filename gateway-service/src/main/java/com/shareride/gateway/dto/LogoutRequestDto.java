package com.shareride.gateway.dto;

import lombok.Data;

@Data
public class LogoutRequestDto {
    private String accessToken;
    private String refreshToken;
    private String sessionId;
}
