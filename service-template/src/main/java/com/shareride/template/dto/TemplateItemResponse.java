package com.shareride.template.dto;

import com.shareride.template.domain.TemplateItemStatus;
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
public class TemplateItemResponse {

    private UUID id;
    private Long version;
    private String name;
    private String code;
    private String description;
    private TemplateItemStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}
