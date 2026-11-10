package com.shareride.auth.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload chứa thông tin phiên và tài khoản gắn liền với Refresh Token được lưu trong Valkey. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenData implements Serializable {

    private UUID sessionId;
    private UUID accountId;
    private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
}
