package com.shareride.auth.domain;

import com.shareride.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends AuditableEntity {

   @Column(name = "user_id", nullable = false)
   private UUID userId;

   @Column(name = "token_hash", nullable = false, length = 255, unique = true)
   private String tokenHash;

   @Column(name = "expires_at", nullable = false)
   private Instant expiresAt;

   @Column(name = "revoked", nullable = false)
   @Builder.Default
   private boolean revoked = false;

}
