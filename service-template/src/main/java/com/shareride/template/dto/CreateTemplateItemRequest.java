package com.shareride.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request payload for creating a new template item. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tạo mới một mục mẫu (Template Item)")
public class CreateTemplateItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 128, message = "Item name cannot exceed 128 characters")
    @Schema(
            description = "Tên hiển thị của mục mẫu",
            example = "Standard Vehicle Type",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Item code is required")
    @Size(max = 64, message = "Item code cannot exceed 64 characters")
    @Schema(
            description = "Mã nghiệp vụ duy nhất của mục mẫu",
            example = "VEHICLE_STANDARD_4S",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Size(max = 512, message = "Description cannot exceed 512 characters")
    @Schema(
            description = "Mô tả chi tiết mục mẫu",
            example = "4-seater standard urban commute vehicle specification")
    private String description;
}
