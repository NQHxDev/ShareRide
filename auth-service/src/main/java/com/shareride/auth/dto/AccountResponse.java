package com.shareride.auth.dto;

import com.shareride.auth.domain.AccountStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi chi tiết thông tin tài khoản người dùng và quyền hạn RBAC. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private UUID accountId;
    private String email;
    private String phoneNumber;
    private AccountStatus status;
    private Set<String> roles;
    private Set<String> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}
