package com.shareride.auth.service.impl;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.PermissionCode;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.repository.PermissionRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.RolePermissionCacheService;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link RolePermissionCacheService} quản lý lưu trữ, truy xuất và duy trì bộ
 * nhớ đệm cho Roles và Permissions trên Valkey dùng chung với TTL 6 tiếng.
 */
@Slf4j
@Service
public class RolePermissionCacheServiceImpl implements RolePermissionCacheService {

    private static final Duration CACHE_TTL = AuthConstants.CacheTtl.ROLE_PERMISSION_VALIDITY;
    private static final JavaType STRING_SET_TYPE =
            TypeFactory.defaultInstance().constructCollectionType(Set.class, String.class);
    private static final JavaType ROLE_LIST_TYPE =
            TypeFactory.defaultInstance().constructCollectionType(List.class, Role.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ObjectMapper objectMapper;

    public RolePermissionCacheServiceImpl(
            @Qualifier("sharedRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            refreshCache();
            log.info("Successfully warmed up Role & Permission cache in Valkey (TTL 6h)");
        } catch (Exception e) {
            log.warn(
                    "Could not warm up Role & Permission cache on startup: {}. Cache will be"
                            + " populated on demand.",
                    e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> getRoleByCode(RoleCode roleCode) {
        if (roleCode == null) {
            return Optional.empty();
        }

        String cacheKey = AuthConstants.CacheKey.ROLE_CODE_PREFIX + roleCode.name();
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw != null) {
                Role cachedRole = objectMapper.convertValue(raw, Role.class);
                return Optional.ofNullable(cachedRole);
            }
        } catch (Exception e) {
            log.warn("Valkey error fetching role [{}]: {}", roleCode, e.getMessage());
        }

        // Cache miss: Truy vấn DB với fetch permissions để tránh N+1 query problem
        Optional<Role> roleOpt = roleRepository.findWithPermissionsByRoleCode(roleCode);
        roleOpt.ifPresent(this::cacheRoleData);
        return roleOpt;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Role> getRolesByCodes(Collection<RoleCode> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Role> roles = new HashSet<>();
        Set<RoleCode> missedCodes = new HashSet<>();

        for (RoleCode code : roleCodes) {
            if (code == null) {
                continue;
            }
            String cacheKey = AuthConstants.CacheKey.ROLE_CODE_PREFIX + code.name();
            try {
                Object raw = redisTemplate.opsForValue().get(cacheKey);
                if (raw != null) {
                    Role role = objectMapper.convertValue(raw, Role.class);
                    if (role != null) {
                        roles.add(role);
                        continue;
                    }
                }
            } catch (Exception e) {
                log.warn("Valkey error fetching role [{}]: {}", code, e.getMessage());
            }
            missedCodes.add(code);
        }

        if (!missedCodes.isEmpty()) {
            Set<Role> dbRoles = roleRepository.findWithPermissionsByRoleCodeIn(missedCodes);
            for (Role dbRole : dbRoles) {
                cacheRoleData(dbRole);
                roles.add(dbRole);
            }
        }

        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getPermissionsByRoleCode(RoleCode roleCode) {
        if (roleCode == null) {
            return Collections.emptySet();
        }

        String cacheKey = AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + roleCode.name();
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw != null) {
                Set<String> permissions = objectMapper.convertValue(raw, STRING_SET_TYPE);
                if (permissions != null) {
                    return permissions;
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Valkey error fetching permissions for role [{}]: {}",
                    roleCode,
                    e.getMessage());
        }

        // Cache miss: Nạp Role và permissions từ DB rồi ghi cache
        return getRoleByCode(roleCode)
                .map(this::extractPermissionCodes)
                .orElse(Collections.emptySet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getPermissionsByRoleCodes(Collection<RoleCode> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> aggregatedPermissions = new HashSet<>();
        for (RoleCode roleCode : roleCodes) {
            if (roleCode != null) {
                aggregatedPermissions.addAll(getPermissionsByRoleCode(roleCode));
            }
        }
        return aggregatedPermissions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Permission> getPermissionByCode(PermissionCode permissionCode) {
        if (permissionCode == null) {
            return Optional.empty();
        }

        String cacheKey = AuthConstants.CacheKey.PERMISSION_CODE_PREFIX + permissionCode.getValue();
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw != null) {
                Permission cachedPermission = objectMapper.convertValue(raw, Permission.class);
                return Optional.ofNullable(cachedPermission);
            }
        } catch (Exception e) {
            log.warn("Valkey error fetching permission [{}]: {}", permissionCode, e.getMessage());
        }

        Optional<Permission> permissionOpt =
                permissionRepository.findByPermissionCode(permissionCode);
        permissionOpt.ifPresent(this::cachePermissionData);
        return permissionOpt;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        String cacheKey = AuthConstants.CacheKey.ALL_ROLES_KEY;
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw != null) {
                List<Role> cachedList = objectMapper.convertValue(raw, ROLE_LIST_TYPE);
                if (cachedList != null) {
                    return cachedList;
                }
            }
        } catch (Exception e) {
            log.warn("Valkey error fetching all roles: {}", e.getMessage());
        }

        List<Role> roles = roleRepository.findAllWithPermissions();
        try {
            // Chuyển sang clean collection trước khi lưu vào Valkey
            for (Role role : roles) {
                cacheRoleData(role);
            }
            redisTemplate.opsForValue().set(cacheKey, roles, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache all roles in Valkey: {}", e.getMessage(), e);
        }
        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public void refreshCache() {
        List<Role> roles = roleRepository.findAllWithPermissions();
        for (Role role : roles) {
            cacheRoleData(role);
        }

        List<Permission> permissions = permissionRepository.findAll();
        for (Permission permission : permissions) {
            cachePermissionData(permission);
        }

        try {
            redisTemplate.opsForValue().set(AuthConstants.CacheKey.ALL_ROLES_KEY, roles, CACHE_TTL);
            redisTemplate
                    .opsForValue()
                    .set(AuthConstants.CacheKey.ALL_PERMISSIONS_KEY, permissions, CACHE_TTL);
            log.info(
                    "Cached [{}] roles and [{}] permissions in Valkey with TTL {}h",
                    roles.size(),
                    permissions.size(),
                    CACHE_TTL.toHours());
        } catch (Exception e) {
            log.warn("Error setting bulk role/permission keys in Valkey: {}", e.getMessage());
        }
    }

    @Override
    public void evictRole(RoleCode roleCode) {
        if (roleCode == null) {
            return;
        }
        try {
            redisTemplate.delete(AuthConstants.CacheKey.ROLE_CODE_PREFIX + roleCode.name());
            redisTemplate.delete(AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + roleCode.name());
            redisTemplate.delete(AuthConstants.CacheKey.ALL_ROLES_KEY);
            log.info("Evicted role cache for [{}]", roleCode);
        } catch (Exception e) {
            log.error("Failed to evict role [{}] from Valkey: {}", roleCode, e.getMessage(), e);
        }
    }

    @Override
    public void evictPermission(PermissionCode permissionCode) {
        if (permissionCode == null) {
            return;
        }
        try {
            redisTemplate.delete(
                    AuthConstants.CacheKey.PERMISSION_CODE_PREFIX + permissionCode.getValue());
            redisTemplate.delete(AuthConstants.CacheKey.ALL_PERMISSIONS_KEY);
            // Xóa permissions của tất cả roles để đảm bảo tính nhất quán
            for (RoleCode code : RoleCode.values()) {
                redisTemplate.delete(AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + code.name());
            }
            log.info("Evicted permission cache for [{}]", permissionCode);
        } catch (Exception e) {
            log.error(
                    "Failed to evict permission [{}] from Valkey: {}",
                    permissionCode,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void evictAll() {
        try {
            for (RoleCode code : RoleCode.values()) {
                redisTemplate.delete(AuthConstants.CacheKey.ROLE_CODE_PREFIX + code.name());
                redisTemplate.delete(AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + code.name());
            }
            for (PermissionCode code : PermissionCode.values()) {
                redisTemplate.delete(
                        AuthConstants.CacheKey.PERMISSION_CODE_PREFIX + code.getValue());
            }
            redisTemplate.delete(AuthConstants.CacheKey.ALL_ROLES_KEY);
            redisTemplate.delete(AuthConstants.CacheKey.ALL_PERMISSIONS_KEY);
            log.info("Evicted all role & permission cache from Valkey");
        } catch (Exception e) {
            log.error("Failed to evict all role & permission cache: {}", e.getMessage(), e);
        }
    }

    private void cacheRoleData(Role role) {
        if (role == null || role.getRoleCode() == null) {
            return;
        }
        String roleKey = AuthConstants.CacheKey.ROLE_CODE_PREFIX + role.getRoleCode().name();
        String permKey = AuthConstants.CacheKey.ROLE_PERMISSIONS_PREFIX + role.getRoleCode().name();

        try {
            // Chuyển PersistentSet sang clean HashSet để an toàn khi serialize vào Valkey
            if (role.getPermissions() != null) {
                role.setPermissions(new HashSet<>(role.getPermissions()));
            }
            redisTemplate.opsForValue().set(roleKey, role, CACHE_TTL);

            Set<String> permissionCodes = extractPermissionCodes(role);
            redisTemplate.opsForValue().set(permKey, permissionCodes, CACHE_TTL);
            if (role.getId() != null) {
                redisTemplate
                        .opsForValue()
                        .set(AuthConstants.CacheKey.ROLE_ID_PREFIX + role.getId(), role, CACHE_TTL);
            }
            log.debug(
                    "Stored role [{}] and [{}] permissions in Valkey with TTL {}h",
                    role.getRoleCode(),
                    permissionCodes.size(),
                    CACHE_TTL.toHours());
        } catch (Exception e) {
            log.error(
                    "Failed to cache role [{}] in Valkey: {}", role.getRoleCode(), e.getMessage());
        }
    }

    private void cachePermissionData(Permission permission) {
        if (permission == null || permission.getPermissionCode() == null) {
            return;
        }
        String key =
                AuthConstants.CacheKey.PERMISSION_CODE_PREFIX
                        + permission.getPermissionCode().getValue();
        try {
            redisTemplate.opsForValue().set(key, permission, CACHE_TTL);
            log.debug(
                    "Stored permission [{}] in Valkey with TTL {}h",
                    permission.getPermissionCode().getValue(),
                    CACHE_TTL.toHours());
        } catch (Exception e) {
            log.error(
                    "Failed to cache permission [{}] in Valkey: {}",
                    permission.getPermissionCode().getValue(),
                    e.getMessage());
        }
    }

    private Set<String> extractPermissionCodes(Role role) {
        if (role == null || role.getPermissions() == null) {
            return Collections.emptySet();
        }
        return role.getPermissions().stream()
                .filter(Objects::nonNull)
                .map(p -> p.getPermissionCode() != null ? p.getPermissionCode().getValue() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
