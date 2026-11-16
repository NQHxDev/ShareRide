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
 * Thực thể thử thách xác thực email tạm thời. Lưu trữ hash SHA-256 của mã xác thực 6 ký tự, theo
 * dõi số lần thử sai, và quản lý vòng đời từ PENDING đến VERIFIED/EXPIRED/MAX_ATTEMPTS_EXCEEDED.
 */
@Entity
@Table(name = "account_verifications")
@AttributeOverride(
        name = "id",
        column = @Column(name = "verification_id", updatable = false, nullable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountVerification extends AuditableEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "verification_code_hash", nullable = false, length = 64)
    private String verificationCodeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;
}
