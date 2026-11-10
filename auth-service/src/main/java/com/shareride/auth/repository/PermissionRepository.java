package com.shareride.auth.repository;

import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.PermissionCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository quản lý quyền hạn chi tiết (Permission) trong cấu trúc RBAC. */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByPermissionCode(PermissionCode permissionCode);

    default Optional<Permission> findByCode(PermissionCode permissionCode) {
        return findByPermissionCode(permissionCode);
    }

    default Optional<Permission> findByCode(String code) {
        PermissionCode permissionCode = PermissionCode.fromValue(code);
        return permissionCode != null ? findByPermissionCode(permissionCode) : Optional.empty();
    }
}
