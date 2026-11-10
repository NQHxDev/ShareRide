package com.shareride.auth.repository;

import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository quản lý vai trò người dùng (Role) trong cấu trúc RBAC. */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleCode(RoleCode roleCode);

    Set<Role> findByRoleCodeIn(Collection<RoleCode> roleCodes);

    default Optional<Role> findByCode(RoleCode roleCode) {
        return findByRoleCode(roleCode);
    }

    default Optional<Role> findByCode(String code) {
        RoleCode roleCode = RoleCode.fromString(code);
        return roleCode != null ? findByRoleCode(roleCode) : Optional.empty();
    }

    default Set<Role> findByCodeIn(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<RoleCode> roleCodes =
                codes.stream()
                        .map(RoleCode::fromString)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return findByRoleCodeIn(roleCodes);
    }
}
