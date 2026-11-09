package com.shareride.template.dto;

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
public class CreateTemplateItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 128, message = "Item name cannot exceed 128 characters")
    private String name;

    @NotBlank(message = "Item code is required")
    @Size(max = 64, message = "Item code cannot exceed 64 characters")
    private String code;

    @Size(max = 512, message = "Description cannot exceed 512 characters")
    private String description;
}
