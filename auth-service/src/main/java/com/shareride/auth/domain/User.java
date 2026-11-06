package com.shareride.auth.domain;

import com.shareride.common.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends SoftDeletableEntity {

   @Column(name = "email", nullable = false, length = 128, unique = true)
   private String email;

   @Column(name = "password_hash", nullable = false, length = 255)
   private String passwordHash;

   @Column(name = "full_name", nullable = false, length = 128)
   private String fullName;

   @Column(name = "phone_number", nullable = false, length = 32, unique = true)
   private String phoneNumber;

   @Enumerated(EnumType.STRING)
   @Column(name = "role", nullable = false, length = 32)
   @Builder.Default
   private UserRole role = UserRole.RIDER;

   @Enumerated(EnumType.STRING)
   @Column(name = "status", nullable = false, length = 32)
   @Builder.Default
   private UserStatus status = UserStatus.ACTIVE;

}
