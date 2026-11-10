package com.shareride.template.dto;

import com.shareride.template.domain.TemplateItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request payload for updating an existing template item. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật thông tin mục mẫu")
public class UpdateTemplateItemRequest {

    @Size(max = 128, message = "Item name cannot exceed 128 characters")
    @Schema(description = "Tên mới của mục mẫu", example = "Updated Vehicle Type")
    private String name;

    @Size(max = 512, message = "Description cannot exceed 512 characters")
    @Schema(description = "Mô tả mới của mục mẫu", example = "Updated vehicle description")
    private String description;

    @Schema(description = "Trạng thái mới của mục mẫu", example = "ACTIVE")
    private TemplateItemStatus status;
}
