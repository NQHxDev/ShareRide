package com.shareride.template.dto;

import com.shareride.template.domain.TemplateItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard response payload representing a template item. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết một mục mẫu")
public class TemplateItemResponse {

    @Schema(
            description = "UUID định danh duy nhất (UUIDv7)",
            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
    private UUID id;

    @Schema(description = "Số phiên bản phục vụ Optimistic Locking", example = "1")
    private Long version;

    @Schema(description = "Tên mục mẫu", example = "Standard Vehicle Type")
    private String name;

    @Schema(description = "Mã nghiệp vụ duy nhất", example = "VEHICLE_STANDARD_4S")
    private String code;

    @Schema(
            description = "Mô tả chi tiết",
            example = "4-seater standard urban commute vehicle specification")
    private String description;

    @Schema(description = "Trạng thái hoạt động", example = "ACTIVE")
    private TemplateItemStatus status;

    @Schema(description = "Thời điểm khởi tạo (UTC)", example = "2026-11-10T12:00:00.000Z")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất (UTC)", example = "2026-11-10T12:00:00.000Z")
    private Instant updatedAt;

    @Schema(description = "Định danh người khởi tạo", example = "SYSTEM")
    private String createdBy;

    @Schema(description = "Định danh người cập nhật gần nhất", example = "SYSTEM")
    private String updatedBy;
}
