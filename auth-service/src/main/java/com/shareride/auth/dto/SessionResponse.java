package com.shareride.auth.dto;

import com.shareride.auth.domain.SessionStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi thông tin thiết bị và phiên đăng nhập (Session) của tài khoản. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private UUID sessionId;
    private UUID accountId;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    private String location;
    private SessionStatus status;
    private Instant lastActiveAt;
    private Instant expiresAt;
    private Instant createdAt;
}
