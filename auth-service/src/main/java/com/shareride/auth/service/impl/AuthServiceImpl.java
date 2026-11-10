package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthSecurityAuditConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.domain.Session;
import com.shareride.auth.domain.SessionStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RefreshTokenData;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.repository.SessionRepository;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.JwtService;
import com.shareride.auth.service.RefreshTokenCacheService;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.cache.DistributedLockService;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link AuthService} xử lý các nghiệp vụ xác thực tài khoản, phân quyền RBAC,
 * và quản lý phiên làm việc theo mô hình Enterprise.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenCacheService refreshTokenCacheService;
    private final JwtService jwtService;
    private final OutboxService outboxService;
    private final DistributedLockService distributedLockService;
    private final AuditLogService auditLogService;

    public AuthServiceImpl(
            AccountRepository accountRepository,
            SessionRepository sessionRepository,
            RoleRepository roleRepository,
            RefreshTokenCacheService refreshTokenCacheService,
            JwtService jwtService,
            OutboxService outboxService,
            ObjectProvider<DistributedLockService> distributedLockServiceProvider,
            AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenCacheService = refreshTokenCacheService;
        this.jwtService = jwtService;
        this.outboxService = outboxService;
        this.distributedLockService = distributedLockServiceProvider.getIfAvailable();
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AccountResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        String lockKey = AuthConstants.CacheKey.REGISTER_EMAIL_LOCK_PREFIX + normalizedEmail;

        if (distributedLockService != null) {
            return distributedLockService.executeWithLock(
                    lockKey,
                    AuthConstants.CacheKey.REGISTER_LOCK_TIMEOUT,
                    () -> doRegister(request, normalizedEmail));
        }
        return doRegister(request, normalizedEmail);
    }

    private AccountResponse doRegister(RegisterRequest request, String normalizedEmail) {
        String normalizedPhone = request.getPhoneNumber().trim();

        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException(
                    AuthResponseCode.EMAIL_ALREADY_EXISTS,
                    AuthMessage.emailAlreadyExists(normalizedEmail));
        }
        if (accountRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new ConflictException(
                    AuthResponseCode.PHONE_ALREADY_EXISTS,
                    AuthMessage.phoneNumberAlreadyExists(normalizedPhone));
        }

        RoleCode desiredRoleCode = RoleCode.fromString(request.getRoleCode());
        RoleCode targetRoleCode = desiredRoleCode != null ? desiredRoleCode : RoleCode.ROLE_RIDER;

        Role role =
                roleRepository
                        .findByRoleCode(targetRoleCode)
                        .orElseGet(
                                () ->
                                        roleRepository
                                                .findByRoleCode(RoleCode.ROLE_RIDER)
                                                .orElse(null));

        Set<Role> roles = new HashSet<>();
        if (role != null) {
            roles.add(role);
        }

        Account account =
                Account.builder()
                        .email(normalizedEmail)
                        .phoneNumber(normalizedPhone)
                        .passwordHash(PasswordUtils.hashPassword(request.getPassword()))
                        .status(AccountStatus.ACTIVE)
                        .roles(roles)
                        .build();

        Account savedAccount = accountRepository.save(account);

        // Lưu sự kiện Outbox trong cùng Transaction để đảm bảo tính nhất quán Dual-write
        List<String> roleCodes =
                savedAccount.getRoles() != null
                        ? savedAccount.getRoles().stream()
                                .filter(Objects::nonNull)
                                .map(r -> r.getRoleCode() != null ? r.getRoleCode().name() : null)
                                .filter(Objects::nonNull)
                                .toList()
                        : List.of();
        Map<String, Object> payload = new HashMap<>();
        payload.put(AuthOutboxConstants.PayloadKey.ACCOUNT_ID, savedAccount.getId());
        payload.put(AuthOutboxConstants.PayloadKey.EMAIL, savedAccount.getEmail());
        payload.put(AuthOutboxConstants.PayloadKey.PHONE_NUMBER, savedAccount.getPhoneNumber());
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            payload.put(AuthOutboxConstants.PayloadKey.FULL_NAME, request.getFullName().trim());
        }
        payload.put(AuthOutboxConstants.PayloadKey.ROLES, roleCodes);

        outboxService.recordEvent(
                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                savedAccount.getId().toString(),
                AuthOutboxConstants.EventType.ACCOUNT_REGISTERED,
                payload);

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.ACCOUNT_REGISTRATION)
                        .userId(savedAccount.getId().toString())
                        .resource(AuthConstants.AuditResource.REGISTER)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        log.info(
                "Successfully registered account [{}] with roles {}",
                savedAccount.getId(),
                roleCodes);
        return toAccountResponse(savedAccount);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        Account account =
                accountRepository
                        .findByEmailAndDeletedFalse(normalizedEmail)
                        .orElseThrow(
                                () -> {
                                    auditLogService.logSecurityEvent(
                                            SecurityAuditEvent.builder()
                                                    .eventType(
                                                            AuthSecurityAuditConstants.EventType
                                                                    .LOGIN_FAILURE)
                                                    .resource(AuthConstants.AuditResource.LOGIN)
                                                    .status(
                                                            AuthSecurityAuditConstants.Status
                                                                    .ACCOUNT_NOT_FOUND)
                                                    .build());
                                    return new UnauthorizedException(
                                            AuthMessage.INVALID_EMAIL_OR_PASSWORD);
                                });

        if (!PasswordUtils.verifyPassword(request.getPassword(), account.getPasswordHash())) {
            auditLogService.logSecurityEvent(
                    SecurityAuditEvent.builder()
                            .eventType(AuthSecurityAuditConstants.EventType.LOGIN_FAILURE)
                            .userId(account.getId().toString())
                            .resource(AuthConstants.AuditResource.LOGIN)
                            .status(AuthSecurityAuditConstants.Status.INVALID_CREDENTIALS)
                            .build());
            throw new UnauthorizedException(AuthMessage.INVALID_EMAIL_OR_PASSWORD);
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException(AuthMessage.accountInactive(account.getStatus()));
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY);

        Session session =
                Session.builder()
                        .accountId(account.getId())
                        .deviceId(request.getDeviceId())
                        .deviceName(request.getDeviceName())
                        .deviceType(request.getDeviceType())
                        .ipAddress(ipAddress)
                        .location(request.getLocation())
                        .userAgent(userAgent)
                        .status(SessionStatus.ACTIVE)
                        .lastActiveAt(now)
                        .expiresAt(expiresAt)
                        .build();

        Session savedSession = sessionRepository.save(session);

        String accessToken = jwtService.generateAccessToken(account, savedSession);
        String refreshToken = UuidUtils.v4AsString();

        refreshTokenCacheService.saveRefreshToken(
                savedSession.getId(),
                account.getId(),
                refreshToken,
                AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY);

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.LOGIN_SUCCESS)
                        .userId(account.getId().toString())
                        .resource(AuthConstants.AuditResource.LOGIN)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresIn(Instant.now().plus(jwtService.getAccessTokenTtl()))
                .sessionId(savedSession.getId())
                .account(toAccountResponse(account))
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken, String ipAddress, String userAgent) {
        RefreshTokenData tokenData =
                refreshTokenCacheService
                        .getRefreshTokenData(refreshToken)
                        .orElseThrow(
                                () -> new UnauthorizedException(AuthMessage.INVALID_REFRESH_TOKEN));

        Session session =
                sessionRepository
                        .findById(tokenData.getSessionId())
                        .orElseThrow(
                                () -> {
                                    refreshTokenCacheService.revokeRefreshToken(refreshToken);
                                    return new UnauthorizedException(
                                            AuthMessage.SESSION_EXPIRED_OR_REVOKED);
                                });

        if (session.getStatus() != SessionStatus.ACTIVE
                || session.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenCacheService.revokeRefreshToken(refreshToken);
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new UnauthorizedException(AuthMessage.SESSION_EXPIRED_OR_REVOKED);
        }

        Account account =
                accountRepository
                        .findById(tokenData.getAccountId())
                        .filter(a -> !a.isDeleted() && a.getStatus() == AccountStatus.ACTIVE)
                        .orElseThrow(
                                () -> {
                                    refreshTokenCacheService.revokeRefreshToken(refreshToken);
                                    return new UnauthorizedException(
                                            AuthMessage.accountInactive("ACCOUNT_DISABLED"));
                                });

        // Token Rotation: Thu hồi token cũ và sinh token mới
        refreshTokenCacheService.revokeRefreshToken(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(account, session);
        String newRefreshToken = UuidUtils.v4AsString();

        session.setLastActiveAt(Instant.now());
        if (ipAddress != null && !ipAddress.isBlank()) {
            session.setIpAddress(ipAddress);
        }
        sessionRepository.save(session);

        refreshTokenCacheService.saveRefreshToken(
                session.getId(),
                account.getId(),
                newRefreshToken,
                AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY);

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.TOKEN_REFRESH)
                        .userId(account.getId().toString())
                        .resource(AuthConstants.AuditResource.REFRESH)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresIn(Instant.now().plus(jwtService.getAccessTokenTtl()))
                .sessionId(session.getId())
                .account(toAccountResponse(account))
                .build();
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            jwtService.revokeToken(accessToken);
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenCacheService
                    .getRefreshTokenData(refreshToken)
                    .ifPresent(
                            data ->
                                    sessionRepository
                                            .findById(data.getSessionId())
                                            .ifPresent(
                                                    s -> {
                                                        s.setStatus(SessionStatus.REVOKED);
                                                        sessionRepository.save(s);
                                                    }));
            refreshTokenCacheService.revokeRefreshToken(refreshToken);
        }

        UserContext context = UserContextHolder.getContext();
        String accountId =
                (context != null && context.getUserId() != null)
                        ? context.getUserId()
                        : AuthConstants.UNKNOWN_ACCOUNT_ID;

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.LOGOUT)
                        .userId(accountId)
                        .resource(AuthConstants.AuditResource.LOGOUT)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        log.info("Account [{}] logged out, session revoked", accountId);
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId, UUID currentAccountId) {
        Session session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (currentAccountId != null && !session.getAccountId().equals(currentAccountId)) {
            throw new UnauthorizedException("Cannot revoke session of another account");
        }

        session.setStatus(SessionStatus.REVOKED);
        sessionRepository.save(session);
        refreshTokenCacheService.revokeSession(sessionId, session.getAccountId());

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.SESSION_REVOKED)
                        .userId(session.getAccountId().toString())
                        .resource(AuthConstants.AuditResource.SESSIONS)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        log.info("Session [{}] revoked for account [{}]", sessionId, session.getAccountId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(UUID accountId) {
        return sessionRepository
                .findByAccountIdAndStatusOrderByLastActiveAtDesc(accountId, SessionStatus.ACTIVE)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        Account account =
                accountRepository
                        .findById(accountId)
                        .filter(a -> !a.isDeleted())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                                                accountId));
        return toAccountResponse(account);
    }

    @Override
    @Transactional
    public void softDeleteAccount(UUID accountId, String deletedBy) {
        Account account =
                accountRepository
                        .findById(accountId)
                        .filter(a -> !a.isDeleted())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                                                accountId));
        account.markDeleted(deletedBy);
        accountRepository.save(account);

        // Hủy tất cả các phiên đăng nhập đang active trên DB và Valkey
        sessionRepository
                .findByAccountIdOrderByLastActiveAtDesc(accountId)
                .forEach(
                        s -> {
                            s.setStatus(SessionStatus.REVOKED);
                            sessionRepository.save(s);
                        });
        refreshTokenCacheService.revokeAllSessions(accountId);

        outboxService.recordEvent(
                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                accountId.toString(),
                AuthOutboxConstants.EventType.ACCOUNT_DELETED,
                Map.of(AuthOutboxConstants.PayloadKey.ACCOUNT_ID, accountId));
        log.info("Account [{}] soft deleted by [{}]", accountId, deletedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getJwks() {
        return jwtService.getJwks();
    }

    private AccountResponse toAccountResponse(Account account) {
        Set<String> roleCodes =
                account.getRoles() != null
                        ? account.getRoles().stream()
                                .filter(Objects::nonNull)
                                .map(
                                        role ->
                                                role.getRoleCode() != null
                                                        ? role.getRoleCode().name()
                                                        : null)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                        : Set.of();

        Set<String> permissions =
                account.getRoles() != null
                        ? account.getRoles().stream()
                                .filter(Objects::nonNull)
                                .filter(r -> r.getPermissions() != null)
                                .flatMap(r -> r.getPermissions().stream())
                                .filter(Objects::nonNull)
                                .map(
                                        permission ->
                                                permission.getPermissionCode() != null
                                                        ? permission.getPermissionCode().getValue()
                                                        : null)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                        : Set.of();

        return AccountResponse.builder()
                .accountId(account.getId())
                .email(account.getEmail())
                .phoneNumber(account.getPhoneNumber())
                .status(account.getStatus())
                .roles(roleCodes)
                .permissions(permissions)
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private SessionResponse toSessionResponse(Session session) {
        return SessionResponse.builder()
                .sessionId(session.getId())
                .accountId(session.getAccountId())
                .deviceId(session.getDeviceId())
                .deviceName(session.getDeviceName())
                .deviceType(session.getDeviceType())
                .ipAddress(session.getIpAddress())
                .location(session.getLocation())
                .status(session.getStatus())
                .lastActiveAt(session.getLastActiveAt())
                .expiresAt(session.getExpiresAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
