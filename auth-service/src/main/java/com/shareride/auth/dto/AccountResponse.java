package com.shareride.auth.dto;

import com.shareride.auth.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin chi tiết tài khoản xác thực và quyền hạn RBAC")
public class AccountResponse {

    @Schema(
            description = "UUID định danh duy nhất của tài khoản (UUIDv7)",
            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
    private UUID accountId;

    @Schema(description = "Địa chỉ email của người dùng", example = "user@example.com")
    private String email;

    @Schema(description = "Số điện thoại đăng ký", example = "+84901234567")
    private String phoneNumber;

    @Schema(description = "Trạng thái hoạt động của tài khoản", example = "ACTIVE")
    private AccountStatus status;

    @Schema(
            description = "Tập hợp các vai trò RBAC được gán",
            example = "[\"ROLE_RIDER\", \"ROLE_USER\"]")
    private Set<String> roles;

    @Schema(
            description = "Tập hợp các quyền hạn chi tiết",
            example = "[\"ride:create\", \"ride:read\"]")
    private Set<String> permissions;

    @Schema(description = "Thời điểm tạo tài khoản (UTC)", example = "2026-11-10T12:00:00.000Z")
    private Instant createdAt;

    @Schema(
            description = "Thời điểm cập nhật tài khoản gần nhất (UTC)",
            example = "2026-11-10T12:00:00.000Z")
    private Instant updatedAt;
}
