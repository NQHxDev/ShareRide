package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.PermissionCode;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.repository.PermissionRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.impl.RolePermissionCacheServiceImpl;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RolePermissionCacheServiceTest {

    private static final Duration SIX_HOURS = Duration.ofHours(6);

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RolePermissionCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService =
                new RolePermissionCacheServiceImpl(
                        redisTemplate, roleRepository, permissionRepository, objectMapper);
    }

    @Test
    @DisplayName("getRoleByCode: Cache Hit - Trả về dữ liệu từ Valkey mà không chạm vào DB")
    void getRoleByCode_whenCacheHit_shouldReturnFromValkey() {
        RoleCode roleCode = RoleCode.ROLE_RIDER;
        String cacheKey = AuthConstants.CacheKey.ROLE_CODE_PREFIX + roleCode.name();

        Role cachedRole =
                Role.builder()
                        .roleCode(roleCode)
                        .roleName("Rider")
                        .permissions(new HashSet<>())
                        .build();
        cachedRole.setId(UUID.randomUUID());

        when(valueOperations.get(cacheKey)).thenReturn(cachedRole);

        Optional<Role> result = cacheService.getRoleByCode(roleCode);

        assertTrue(result.isPresent());
        assertEquals(roleCode, result.get().getRoleCode());
        verify(roleRepository, never()).findWithPermissionsByRoleCode(any());
    }

    @Test
    @DisplayName(
            "getRoleByCode: Cache Miss - Truy vấn DB với fetch permissions và lưu cache Valkey 6"
                    + " tiếng")
    void getRoleByCode_whenCacheMiss_shouldLoadFromDbAndCacheFor6Hours() {
        RoleCode roleCode = RoleCode.ROLE_DRIVER;
        String roleKey = AuthConstants.CacheKey.ROLE_CODE_PREFIX + roleCode.name();
        String permKey = AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + roleCode.name();

        Permission perm =
                Permission.builder()
                        .permissionCode(PermissionCode.RIDE_ACCEPT)
                        .permissionName("Accept Ride")
                        .build();
        perm.setId(UUID.randomUUID());

        Set<Permission> perms = new HashSet<>();
        perms.add(perm);

        Role dbRole =
                Role.builder().roleCode(roleCode).roleName("Driver").permissions(perms).build();
        UUID roleId = UUID.randomUUID();
        dbRole.setId(roleId);

        when(valueOperations.get(roleKey)).thenReturn(null);
        when(roleRepository.findWithPermissionsByRoleCode(roleCode))
                .thenReturn(Optional.of(dbRole));

        Optional<Role> result = cacheService.getRoleByCode(roleCode);

        assertTrue(result.isPresent());
        assertEquals(roleCode, result.get().getRoleCode());

        // Xác nhận đã lưu cache với TTL 6 tiếng (21600 giây)
        verify(valueOperations).set(eq(roleKey), eq(dbRole), eq(SIX_HOURS));
        verify(valueOperations)
                .set(eq(permKey), eq(Set.of(PermissionCode.RIDE_ACCEPT.getValue())), eq(SIX_HOURS));
        verify(valueOperations)
                .set(eq(AuthConstants.CacheKey.ROLE_ID_PREFIX + roleId), eq(dbRole), eq(SIX_HOURS));
    }

    @Test
    @DisplayName("getPermissionsByRoleCode: Cache Hit - Trả về tập hợp permission từ Valkey")
    void getPermissionsByRoleCode_whenCacheHit_shouldReturnPermissions() {
        RoleCode roleCode = RoleCode.ROLE_RIDER;
        String permKey = AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + roleCode.name();
        Set<String> cachedPermissions = Set.of("ride:create", "ride:read");

        when(valueOperations.get(permKey)).thenReturn(cachedPermissions);

        Set<String> permissions = cacheService.getPermissionsByRoleCode(roleCode);

        assertEquals(2, permissions.size());
        assertTrue(permissions.contains("ride:create"));
        assertTrue(permissions.contains("ride:read"));
        verify(roleRepository, never()).findWithPermissionsByRoleCode(any());
    }

    @Test
    @DisplayName("getPermissionsByRoleCodes: Tổng hợp permissions từ nhiều roles sử dụng cache")
    void getPermissionsByRoleCodes_shouldAggregateFromMultipleRoles() {
        String riderKey =
                AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + RoleCode.ROLE_RIDER.name();
        String driverKey =
                AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + RoleCode.ROLE_DRIVER.name();

        when(valueOperations.get(riderKey)).thenReturn(Set.of("ride:create", "ride:read"));
        when(valueOperations.get(driverKey)).thenReturn(Set.of("ride:read", "ride:accept"));

        Set<String> permissions =
                cacheService.getPermissionsByRoleCodes(
                        List.of(RoleCode.ROLE_RIDER, RoleCode.ROLE_DRIVER));

        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("ride:create"));
        assertTrue(permissions.contains("ride:read"));
        assertTrue(permissions.contains("ride:accept"));
    }

    @Test
    @DisplayName("getPermissionByCode: Cache Miss - Tải từ DB và lưu cache Valkey 6 tiếng")
    void getPermissionByCode_whenCacheMiss_shouldLoadAndCache() {
        PermissionCode code = PermissionCode.RIDE_CREATE;
        String cacheKey = AuthConstants.CacheKey.PERMISSION_CODE_PREFIX + code.getValue();

        Permission dbPermission =
                Permission.builder().permissionCode(code).permissionName("Create Ride").build();
        dbPermission.setId(UUID.randomUUID());

        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(permissionRepository.findByPermissionCode(code)).thenReturn(Optional.of(dbPermission));

        Optional<Permission> result = cacheService.getPermissionByCode(code);

        assertTrue(result.isPresent());
        assertEquals(code, result.get().getPermissionCode());
        verify(valueOperations).set(eq(cacheKey), eq(dbPermission), eq(SIX_HOURS));
    }

    @Test
    @DisplayName("refreshCache: Nạp trước toàn bộ Roles và Permissions vào Valkey với TTL 6 tiếng")
    void refreshCache_shouldWarmUpAllDataWith6HoursTtl() {
        Role role =
                Role.builder()
                        .roleCode(RoleCode.ROLE_ADMIN)
                        .roleName("Admin")
                        .permissions(new HashSet<>())
                        .build();
        role.setId(UUID.randomUUID());

        Permission perm =
                Permission.builder()
                        .permissionCode(PermissionCode.ACCOUNT_READ)
                        .permissionName("Read Account")
                        .build();
        perm.setId(UUID.randomUUID());

        when(roleRepository.findAllWithPermissions()).thenReturn(List.of(role));
        when(permissionRepository.findAll()).thenReturn(List.of(perm));

        cacheService.refreshCache();

        verify(valueOperations)
                .set(
                        eq(AuthConstants.CacheKey.ROLE_CODE_PREFIX + RoleCode.ROLE_ADMIN.name()),
                        any(),
                        eq(SIX_HOURS));
        verify(valueOperations)
                .set(
                        eq(
                                AuthConstants.CacheKey.PERMISSION_CODE_PREFIX
                                        + PermissionCode.ACCOUNT_READ.getValue()),
                        any(),
                        eq(SIX_HOURS));
        verify(valueOperations)
                .set(eq(AuthConstants.CacheKey.ALL_ROLES_KEY), eq(List.of(role)), eq(SIX_HOURS));
        verify(valueOperations)
                .set(
                        eq(AuthConstants.CacheKey.ALL_PERMISSIONS_KEY),
                        eq(List.of(perm)),
                        eq(SIX_HOURS));
    }

    @Test
    @DisplayName("evictRole: Xóa cache của Role cụ thể trên Valkey")
    void evictRole_shouldDeleteKeys() {
        RoleCode roleCode = RoleCode.ROLE_RIDER;

        cacheService.evictRole(roleCode);

        verify(redisTemplate).delete(AuthConstants.CacheKey.ROLE_CODE_PREFIX + roleCode.name());
        verify(redisTemplate)
                .delete(AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + roleCode.name());
        verify(redisTemplate).delete(AuthConstants.CacheKey.ALL_ROLES_KEY);
    }

    @Test
    @DisplayName("evictAll: Xóa toàn bộ cache Role và Permission")
    void evictAll_shouldClearAllKeys() {
        cacheService.evictAll();

        verify(redisTemplate).delete(AuthConstants.CacheKey.ALL_ROLES_KEY);
        verify(redisTemplate).delete(AuthConstants.CacheKey.ALL_PERMISSIONS_KEY);
        for (RoleCode code : RoleCode.values()) {
            verify(redisTemplate).delete(AuthConstants.CacheKey.ROLE_CODE_PREFIX + code.name());
            verify(redisTemplate)
                    .delete(AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + code.name());
        }
    }

    @Test
    @DisplayName("Valkey lỗi ngoại lệ: Fallback mượt mà về DB mà không làm gián đoạn hệ thống")
    void whenValkeyThrowsException_shouldFallbackToDb() {
        RoleCode roleCode = RoleCode.ROLE_RIDER;
        String cacheKey = AuthConstants.CacheKey.ROLE_CODE_PREFIX + roleCode.name();

        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Redis connection timed out"));
        Role dbRole = Role.builder().roleCode(roleCode).roleName("Rider").build();
        when(roleRepository.findWithPermissionsByRoleCode(roleCode))
                .thenReturn(Optional.of(dbRole));

        Optional<Role> result = cacheService.getRoleByCode(roleCode);

        assertTrue(result.isPresent());
        assertEquals(roleCode, result.get().getRoleCode());
        verify(roleRepository).findWithPermissionsByRoleCode(roleCode);
    }

    @Test
    @DisplayName("getRoleByCode: Khi tham số null trả về Optional.empty")
    void getRoleByCode_whenNull_shouldReturnEmpty() {
        assertTrue(cacheService.getRoleByCode(null).isEmpty());
    }

    @Test
    @DisplayName("getRolesByCodes: Khi danh sách null hoặc rỗng")
    void getRolesByCodes_whenNullOrEmpty_shouldReturnEmptySet() {
        assertTrue(cacheService.getRolesByCodes(null).isEmpty());
        assertTrue(cacheService.getRolesByCodes(List.of()).isEmpty());
    }

    @Test
    @DisplayName("getRolesByCodes: Kết hợp Cache Hit và Cache Miss tải từ DB")
    void getRolesByCodes_mixedCacheHitAndMiss_shouldLoadFromDbAndCache() {
        RoleCode hitCode = RoleCode.ROLE_RIDER;
        RoleCode missCode = RoleCode.ROLE_DRIVER;

        Role hitRole =
                Role.builder()
                        .roleCode(hitCode)
                        .roleName("Rider")
                        .permissions(new HashSet<>())
                        .build();
        hitRole.setId(UUID.randomUUID());

        Role missRole =
                Role.builder()
                        .roleCode(missCode)
                        .roleName("Driver")
                        .permissions(new HashSet<>())
                        .build();
        missRole.setId(UUID.randomUUID());

        when(valueOperations.get(AuthConstants.CacheKey.ROLE_CODE_PREFIX + hitCode.name()))
                .thenReturn(hitRole);
        when(valueOperations.get(AuthConstants.CacheKey.ROLE_CODE_PREFIX + missCode.name()))
                .thenReturn(null);
        when(roleRepository.findWithPermissionsByRoleCodeIn(Set.of(missCode)))
                .thenReturn(Set.of(missRole));

        java.util.List<RoleCode> codes = new java.util.ArrayList<>();
        codes.add(hitCode);
        codes.add(null);
        codes.add(missCode);

        Set<Role> result = cacheService.getRolesByCodes(codes);
        assertEquals(2, result.size());
        verify(valueOperations)
                .set(
                        eq(AuthConstants.CacheKey.ROLE_CODE_PREFIX + missCode.name()),
                        eq(missRole),
                        eq(SIX_HOURS));
    }

    @Test
    @DisplayName("getRolesByCodes: Khi Redis ném exception cho một mã role")
    void getRolesByCodes_whenRedisThrows_shouldFallbackToDb() {
        RoleCode code = RoleCode.ROLE_ADMIN;
        when(valueOperations.get(AuthConstants.CacheKey.ROLE_CODE_PREFIX + code.name()))
                .thenThrow(new RuntimeException("Redis timeout"));

        Role dbRole =
                Role.builder()
                        .roleCode(code)
                        .roleName("Admin")
                        .permissions(new HashSet<>())
                        .build();
        when(roleRepository.findWithPermissionsByRoleCodeIn(Set.of(code)))
                .thenReturn(Set.of(dbRole));

        Set<Role> result = cacheService.getRolesByCodes(List.of(code));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getPermissionsByRoleCode: Khi null trả về tập rỗng")
    void getPermissionsByRoleCode_whenNull_shouldReturnEmpty() {
        assertTrue(cacheService.getPermissionsByRoleCode(null).isEmpty());
    }

    @Test
    @DisplayName("getPermissionsByRoleCode: Cache Miss - Tải role từ DB rồi trích xuất permissions")
    void getPermissionsByRoleCode_whenCacheMiss_shouldLoadFromRoleAndExtract() {
        RoleCode code = RoleCode.ROLE_ADMIN;
        String permKey = AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + code.name();
        when(valueOperations.get(permKey)).thenReturn(null);

        Permission p1 = Permission.builder().permissionCode(PermissionCode.ACCOUNT_READ).build();
        Role dbRole = Role.builder().roleCode(code).permissions(Set.of(p1)).build();
        dbRole.setId(UUID.randomUUID());
        when(roleRepository.findWithPermissionsByRoleCode(code)).thenReturn(Optional.of(dbRole));

        Set<String> permissions = cacheService.getPermissionsByRoleCode(code);
        assertEquals(1, permissions.size());
        assertTrue(permissions.contains(PermissionCode.ACCOUNT_READ.getValue()));
    }

    @Test
    @DisplayName("getPermissionsByRoleCode: Khi Redis ném exception")
    void getPermissionsByRoleCode_whenRedisThrows_shouldFallbackGracefully() {
        RoleCode code = RoleCode.ROLE_ADMIN;
        String permKey = AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + code.name();
        when(valueOperations.get(permKey)).thenThrow(new RuntimeException("Redis down"));

        when(roleRepository.findWithPermissionsByRoleCode(code)).thenReturn(Optional.empty());

        Set<String> permissions = cacheService.getPermissionsByRoleCode(code);
        assertTrue(permissions.isEmpty());
    }

    @Test
    @DisplayName("getPermissionsByRoleCodes: Khi danh sách null hoặc rỗng hoặc chứa phần tử null")
    void getPermissionsByRoleCodes_edgeCases() {
        assertTrue(cacheService.getPermissionsByRoleCodes(null).isEmpty());
        assertTrue(cacheService.getPermissionsByRoleCodes(List.of()).isEmpty());

        java.util.List<RoleCode> listWithNull = new java.util.ArrayList<>();
        listWithNull.add(null);
        assertTrue(cacheService.getPermissionsByRoleCodes(listWithNull).isEmpty());
    }

    @Test
    @DisplayName("getPermissionByCode: Khi tham số null hoặc Cache Hit hoặc Redis exception")
    void getPermissionByCode_variants() {
        assertTrue(cacheService.getPermissionByCode(null).isEmpty());

        PermissionCode code = PermissionCode.RIDE_ACCEPT;
        String cacheKey = AuthConstants.CacheKey.PERMISSION_CODE_PREFIX + code.getValue();
        Permission cached = Permission.builder().permissionCode(code).build();
        when(valueOperations.get(cacheKey)).thenReturn(cached);

        Optional<Permission> result = cacheService.getPermissionByCode(code);
        assertTrue(result.isPresent());
        assertEquals(code, result.get().getPermissionCode());

        // Redis throws
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Connection error"));
        when(permissionRepository.findByPermissionCode(code)).thenReturn(Optional.of(cached));
        Optional<Permission> fallbackResult = cacheService.getPermissionByCode(code);
        assertTrue(fallbackResult.isPresent());
    }

    @Test
    @DisplayName("getAllRoles: Cache Hit")
    void getAllRoles_whenCacheHit_shouldReturnCachedList() {
        String cacheKey = AuthConstants.CacheKey.ALL_ROLES_KEY;
        Role r = Role.builder().roleCode(RoleCode.ROLE_RIDER).build();
        when(valueOperations.get(cacheKey)).thenReturn(List.of(r));

        List<Role> roles = cacheService.getAllRoles();
        assertEquals(1, roles.size());
        verify(roleRepository, never()).findAllWithPermissions();
    }

    @Test
    @DisplayName("getAllRoles: Cache Miss - Tải từ DB và lưu cache")
    void getAllRoles_whenCacheMiss_shouldLoadFromDbAndCache() {
        String cacheKey = AuthConstants.CacheKey.ALL_ROLES_KEY;
        when(valueOperations.get(cacheKey)).thenReturn(null);

        Role r = Role.builder().roleCode(RoleCode.ROLE_RIDER).permissions(new HashSet<>()).build();
        r.setId(UUID.randomUUID());
        when(roleRepository.findAllWithPermissions()).thenReturn(List.of(r));

        List<Role> roles = cacheService.getAllRoles();
        assertEquals(1, roles.size());
        verify(valueOperations).set(eq(cacheKey), eq(List.of(r)), eq(SIX_HOURS));
    }

    @Test
    @DisplayName("getAllRoles: Khi Redis get hoặc set ném exception")
    void getAllRoles_whenRedisThrowsException() {
        String cacheKey = AuthConstants.CacheKey.ALL_ROLES_KEY;
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis error"));

        Role r = Role.builder().roleCode(RoleCode.ROLE_RIDER).permissions(new HashSet<>()).build();
        when(roleRepository.findAllWithPermissions()).thenReturn(List.of(r));

        List<Role> roles = cacheService.getAllRoles();
        assertEquals(1, roles.size());
    }

    @Test
    @DisplayName(
            "onApplicationReady: Khởi động nạp trước cache thành công và xử lý ngoại lệ an toàn")
    void onApplicationReady_scenarios() {
        RolePermissionCacheServiceImpl impl = (RolePermissionCacheServiceImpl) cacheService;

        // Success
        when(roleRepository.findAllWithPermissions()).thenReturn(List.of());
        when(permissionRepository.findAll()).thenReturn(List.of());
        impl.onApplicationReady();

        // Exception
        when(roleRepository.findAllWithPermissions()).thenThrow(new RuntimeException("DB offline"));
        impl.onApplicationReady();
    }

    @Test
    @DisplayName(
            "evictRole và evictPermission: Xử lý an toàn khi tham số null hoặc Redis ném ngoại lệ")
    void evictMethods_nullAndExceptionHandling() {
        cacheService.evictRole(null);
        cacheService.evictPermission(null);

        // Redis throws on delete
        when(redisTemplate.delete(any(String.class)))
                .thenThrow(new RuntimeException("Delete failed"));
        cacheService.evictRole(RoleCode.ROLE_RIDER);
        cacheService.evictPermission(PermissionCode.RIDE_CREATE);
        cacheService.evictAll();
    }
}
