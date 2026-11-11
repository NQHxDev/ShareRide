package com.shareride.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RolePermissionDomainTest {

    @Test
    @DisplayName("Role: Builders, getters, setters and backwards-compatible methods")
    void testRoleDomain() {
        Role role1 = new Role();
        role1.setId(UUID.randomUUID());
        role1.setCode(RoleCode.ROLE_RIDER);
        role1.setName("Rider Role");
        role1.setDescription("Can book rides");

        assertEquals(RoleCode.ROLE_RIDER, role1.getCode());
        assertEquals(RoleCode.ROLE_RIDER, role1.getRoleCode());
        assertEquals("Rider Role", role1.getName());
        assertEquals("Rider Role", role1.getRoleName());
        assertEquals("Can book rides", role1.getDescription());

        // Test builder methods
        Role role2 =
                Role.builder()
                        .roleCode(RoleCode.ROLE_DRIVER)
                        .name("Driver")
                        .description("Can drive")
                        .permissions(new HashSet<>())
                        .build();
        assertEquals(RoleCode.ROLE_DRIVER, role2.getRoleCode());

        Role role3 = Role.builder().code(RoleCode.ROLE_ADMIN).build();
        assertEquals(RoleCode.ROLE_ADMIN, role3.getRoleCode());

        Role role4 = Role.builder().code("ROLE_SUPPORT").build();
        assertEquals(RoleCode.ROLE_SUPPORT, role4.getRoleCode());
    }

    @Test
    @DisplayName("Permission: Builders, getters, setters and backwards-compatible methods")
    void testPermissionDomain() {
        Permission perm1 = new Permission();
        perm1.setId(UUID.randomUUID());
        perm1.setCode(PermissionCode.RIDE_CREATE);
        perm1.setName("Create Ride");
        perm1.setDescription("Create new ride request");

        assertEquals(PermissionCode.RIDE_CREATE, perm1.getCode());
        assertEquals(PermissionCode.RIDE_CREATE, perm1.getPermissionCode());
        assertEquals("Create Ride", perm1.getName());
        assertEquals("Create Ride", perm1.getPermissionName());
        assertEquals("Create new ride request", perm1.getDescription());

        Permission perm2 =
                Permission.builder()
                        .permissionCode(PermissionCode.RIDE_ACCEPT)
                        .name("Accept Ride")
                        .build();
        assertEquals(PermissionCode.RIDE_ACCEPT, perm2.getPermissionCode());

        Permission perm3 = Permission.builder().code(PermissionCode.RIDE_READ).build();
        assertEquals(PermissionCode.RIDE_READ, perm3.getPermissionCode());

        Permission perm4 = Permission.builder().code("account:read").build();
        assertEquals(PermissionCode.ACCOUNT_READ, perm4.getPermissionCode());
    }

    @Test
    @DisplayName("RoleCode: fromString handles normal, case-insensitive, and invalid values")
    void testRoleCodeFromString() {
        assertEquals(RoleCode.ROLE_RIDER, RoleCode.fromString("ROLE_RIDER"));
        assertEquals(RoleCode.ROLE_RIDER, RoleCode.fromString("role_rider"));
        assertEquals(RoleCode.ROLE_DRIVER, RoleCode.fromString("ROLE_DRIVER"));
        assertEquals(RoleCode.ROLE_ADMIN, RoleCode.fromString("ROLE_ADMIN"));
        assertEquals(RoleCode.ROLE_SUPPORT, RoleCode.fromString("ROLE_SUPPORT"));

        assertNull(RoleCode.fromString(null));
        assertNull(RoleCode.fromString("   "));
        assertNull(RoleCode.fromString("NON_EXISTENT_ROLE"));
    }

    @Test
    @DisplayName("PermissionCodeConverter: converts to/from database column")
    void testPermissionCodeConverter() {
        PermissionCodeConverter converter = new PermissionCodeConverter();

        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals("ride:create", converter.convertToDatabaseColumn(PermissionCode.RIDE_CREATE));

        assertNull(converter.convertToEntityAttribute(null));
        assertEquals(PermissionCode.RIDE_CREATE, converter.convertToEntityAttribute("ride:create"));
    }
}
