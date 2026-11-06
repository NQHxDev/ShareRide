package com.shareride.auth.dto;

import com.shareride.auth.domain.UserRole;
import com.shareride.auth.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

   private UUID id;
   private String email;
   private String fullName;
   private String phoneNumber;
   private UserRole role;
   private UserStatus status;
   private Instant createdAt;
   private Instant updatedAt;

}
