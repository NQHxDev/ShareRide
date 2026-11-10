package com.shareride.auth.domain;

import com.shareride.common.persistence.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quản lý phiên đăng nhập (Session) của tài khoản trên từng thiết bị.
 *
 * <p>Bảng này chỉ lưu metadata để theo dõi thiết bị, vị trí và trạng thái đăng nhập. Refresh Token
 * thực tế được lưu trên Valkey (Redis) kèm thời gian sống (TTL).
 */
@Entity
@Table(name = "sessions")
@AttributeOverride(
        name = "id",
        column = @Column(name = "session_id", updatable = false, nullable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session extends AuditableEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "device_name", length = 128)
    private String deviceName;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
