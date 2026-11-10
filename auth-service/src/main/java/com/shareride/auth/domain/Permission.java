package com.shareride.auth.domain;

import com.shareride.common.persistence.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Định nghĩa quyền hạn chi tiết (Granular Permission) trong mô hình RBAC. */
@Entity
@Table(name = "permissions")
@AttributeOverride(
        name = "id",
        column = @Column(name = "permission_id", updatable = false, nullable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends AuditableEntity {

    @Convert(converter = PermissionCodeConverter.class)
    @Column(name = "permission_code", nullable = false, length = 64, unique = true)
    private PermissionCode permissionCode;

    @Column(name = "permission_name", nullable = false, length = 128)
    private String permissionName;

    @Column(name = "description", length = 255)
    private String description;

    /** Tương thích ngược với các lời gọi getCode(). */
    public PermissionCode getCode() {
        return permissionCode;
    }

    /** Tương thích ngược với các lời gọi setCode(). */
    public void setCode(PermissionCode code) {
        this.permissionCode = code;
    }

    /** Tương thích ngược với các lời gọi getName(). */
    public String getName() {
        return permissionName;
    }

    /** Tương thích ngược với các lời gọi setName(). */
    public void setName(String name) {
        this.permissionName = name;
    }

    public static class PermissionBuilder {
        public PermissionBuilder permissionCode(PermissionCode permissionCode) {
            this.permissionCode = permissionCode;
            return this;
        }

        public PermissionBuilder code(PermissionCode code) {
            this.permissionCode = code;
            return this;
        }

        public PermissionBuilder code(String code) {
            this.permissionCode = PermissionCode.fromValue(code);
            return this;
        }

        public PermissionBuilder name(String name) {
            this.permissionName = name;
            return this;
        }
    }
}
