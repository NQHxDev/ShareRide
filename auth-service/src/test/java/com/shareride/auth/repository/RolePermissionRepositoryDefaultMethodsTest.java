package com.shareride.auth.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.PermissionCode;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePermissionRepositoryDefaultMethodsTest {

    @Test
    void testRoleRepositoryDefaultMethods() {
        RoleRepository roleRepository =
                mock(
                        RoleRepository.class,
                        withSettings().defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));

        Role rider = Role.builder().roleCode(RoleCode.ROLE_RIDER).build();
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(rider));
        when(roleRepository.findByRoleCodeIn(Set.of(RoleCode.ROLE_RIDER)))
                .thenReturn(Set.of(rider));

        assertEquals(Optional.of(rider), roleRepository.findByCode(RoleCode.ROLE_RIDER));
        assertEquals(Optional.of(rider), roleRepository.findByCode("ROLE_RIDER"));
        assertTrue(roleRepository.findByCode((String) null).isEmpty());
        assertTrue(roleRepository.findByCode("INVALID").isEmpty());

        assertTrue(roleRepository.findByCodeIn(null).isEmpty());
        assertTrue(roleRepository.findByCodeIn(List.of()).isEmpty());
        assertEquals(Set.of(rider), roleRepository.findByCodeIn(List.of("ROLE_RIDER")));
    }

    @Test
    void testPermissionRepositoryDefaultMethods() {
        PermissionRepository permissionRepository =
                mock(
                        PermissionRepository.class,
                        withSettings().defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));

        Permission perm = Permission.builder().permissionCode(PermissionCode.RIDE_CREATE).build();
        when(permissionRepository.findByPermissionCode(PermissionCode.RIDE_CREATE))
                .thenReturn(Optional.of(perm));

        assertEquals(
                Optional.of(perm), permissionRepository.findByCode(PermissionCode.RIDE_CREATE));
        assertEquals(Optional.of(perm), permissionRepository.findByCode("ride:create"));
        assertTrue(permissionRepository.findByCode((String) null).isEmpty());
        assertTrue(permissionRepository.findByCode("unknown:perm").isEmpty());
    }
}
