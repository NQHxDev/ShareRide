package com.shareride.auth.dto;

import com.shareride.auth.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin phiên đăng nhập trên thiết bị")
public class SessionResponse {

    @Schema(
            description = "UUID định danh phiên làm việc (UUIDv7)",
            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
    private UUID sessionId;

    @Schema(
            description = "UUID tài khoản sở hữu phiên",
            example = "018d9f1e-8742-7ef1-95c2-f123456789ac")
    private UUID accountId;

    @Schema(description = "Mã định danh thiết bị", example = "d8e3c540-1e56-4993-96b4-ff73df7819ad")
    private String deviceId;

    @Schema(description = "Tên thiết bị", example = "iPhone 15 Pro")
    private String deviceName;

    @Schema(description = "Loại thiết bị", example = "MOBILE")
    private String deviceType;

    @Schema(description = "Địa chỉ IP khi khởi tạo hoặc sử dụng phiên", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "Vị trí địa lý ước tính", example = "Hanoi, Vietnam")
    private String location;

    @Schema(
            description = "Trạng thái phiên làm việc (ACTIVE, REVOKED, EXPIRED, TERMINATED)",
            example = "ACTIVE")
    private SessionStatus status;

    @Schema(
            description = "Thời điểm hoạt động gần nhất (UTC)",
            example = "2026-11-10T12:05:00.000Z")
    private Instant lastActiveAt;

    @Schema(description = "Thời điểm phiên hết hạn (UTC)", example = "2026-11-17T12:00:00.000Z")
    private Instant expiresAt;

    @Schema(description = "Thời điểm khởi tạo phiên (UTC)", example = "2026-11-10T12:00:00.000Z")
    private Instant createdAt;
}
