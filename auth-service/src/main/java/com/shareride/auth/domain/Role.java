package com.shareride.auth.domain;

import com.shareride.common.persistence.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Định nghĩa vai trò (Role) trong mô hình RBAC của Auth Service. */
@Entity
@Table(name = "roles")
@AttributeOverride(
        name = "id",
        column = @Column(name = "role_id", updatable = false, nullable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 64, unique = true)
    private RoleCode roleCode;

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /** Tương thích ngược với các lời gọi getCode(). */
    public RoleCode getCode() {
        return roleCode;
    }

    /** Tương thích ngược với các lời gọi setCode(). */
    public void setCode(RoleCode code) {
        this.roleCode = code;
    }

    /** Tương thích ngược với các lời gọi getName(). */
    public String getName() {
        return roleName;
    }

    /** Tương thích ngược với các lời gọi setName(). */
    public void setName(String name) {
        this.roleName = name;
    }

    public static class RoleBuilder {
        public RoleBuilder roleCode(RoleCode roleCode) {
            this.roleCode = roleCode;
            return this;
        }

        public RoleBuilder code(RoleCode code) {
            this.roleCode = code;
            return this;
        }

        public RoleBuilder code(String code) {
            this.roleCode = RoleCode.fromString(code);
            return this;
        }

        public RoleBuilder name(String name) {
            this.roleName = name;
            return this;
        }
    }
}
