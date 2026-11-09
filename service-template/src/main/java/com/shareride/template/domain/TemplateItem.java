package com.shareride.template.domain;

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

/**
 * Core entity for Template Service demonstrating enterprise persistence patterns:
 *
 * <ul>
 *   <li>UUIDv7 Time-ordered primary key (RFC 9562)
 *   <li>Optimistic Locking via @Version
 *   <li>Auditing metadata (createdAt, updatedAt, createdBy, updatedBy)
 *   <li>Soft-delete capability with full audit trail
 * </ul>
 */
@Entity
@Table(name = "template_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateItem extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TemplateItemStatus status = TemplateItemStatus.ACTIVE;
}
