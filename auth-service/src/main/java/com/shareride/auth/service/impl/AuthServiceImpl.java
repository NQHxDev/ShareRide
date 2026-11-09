package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthSecurityAuditConstants;
import com.shareride.auth.domain.RefreshToken;
import com.shareride.auth.domain.User;
import com.shareride.auth.domain.UserRole;
import com.shareride.auth.domain.UserStatus;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.UserResponse;
import com.shareride.auth.repository.RefreshTokenRepository;
import com.shareride.auth.repository.UserRepository;
import com.shareride.auth.service.AuthService;
import com.shareride.common.cache.DistributedLockService;
import com.shareride.common.cache.TokenBlacklistService;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import com.shareride.common.outbox.OutboxService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link AuthService} xử lý các nghiệp vụ xác thực người dùng, bảo mật và quản
 * trị tài khoản trong Auth Service.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxService outboxService;
    private final DistributedLockService distributedLockService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditLogService auditLogService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OutboxService outboxService,
            ObjectProvider<DistributedLockService> distributedLockServiceProvider,
            ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxService = outboxService;
        this.distributedLockService = distributedLockServiceProvider.getIfAvailable();
        this.tokenBlacklistService = tokenBlacklistServiceProvider.getIfAvailable();
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String lockKey =
                AuthConstants.RedisLock.REGISTER_EMAIL_PREFIX
                        + request.getEmail().toLowerCase().trim();

        if (distributedLockService != null) {
            return distributedLockService.executeWithLock(
                    lockKey,
                    AuthConstants.RedisLock.REGISTER_LOCK_TIMEOUT,
                    () -> doRegister(request));
        }
        return doRegister(request);
    }

    private UserResponse doRegister(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException(
                    AuthResponseCode.EMAIL_ALREADY_EXISTS,
                    AuthMessage.emailAlreadyExists(normalizedEmail));
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ConflictException(
                    AuthResponseCode.PHONE_ALREADY_EXISTS,
                    AuthMessage.phoneNumberAlreadyExists(request.getPhoneNumber()));
        }

        User user =
                User.builder()
                        .email(normalizedEmail)
                        .passwordHash(hashPassword(request.getPassword()))
                        .fullName(request.getFullName().trim())
                        .phoneNumber(request.getPhoneNumber().trim())
                        .role(request.getRole() != null ? request.getRole() : UserRole.RIDER)
                        .status(UserStatus.ACTIVE)
                        .build();

        User savedUser = userRepository.save(user);

        // Lưu sự kiện Outbox trong cùng một Transaction để đảm bảo tính nhất quán Dual-write
        outboxService.recordEvent(
                AuthOutboxConstants.AGGREGATE_TYPE_USER,
                savedUser.getId().toString(),
                AuthOutboxConstants.EventType.USER_REGISTERED,
                Map.of(
                        AuthOutboxConstants.PayloadKey.USER_ID, savedUser.getId(),
                        AuthOutboxConstants.PayloadKey.EMAIL, savedUser.getEmail(),
                        AuthOutboxConstants.PayloadKey.FULL_NAME, savedUser.getFullName(),
                        AuthOutboxConstants.PayloadKey.ROLE, savedUser.getRole().name()));

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.USER_REGISTRATION)
                        .userId(savedUser.getId().toString())
                        .resource(AuthConstants.AuditResource.REGISTER)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        log.info(
                "Successfully registered user [{}] with role [{}]",
                savedUser.getId(),
                savedUser.getRole());
        return toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        User user =
                userRepository
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
                                                                    .USER_NOT_FOUND)
                                                    .build());
                                    return new UnauthorizedException(
                                            AuthMessage.INVALID_EMAIL_OR_PASSWORD);
                                });

        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            auditLogService.logSecurityEvent(
                    SecurityAuditEvent.builder()
                            .eventType(AuthSecurityAuditConstants.EventType.LOGIN_FAILURE)
                            .userId(user.getId().toString())
                            .resource(AuthConstants.AuditResource.LOGIN)
                            .status(AuthSecurityAuditConstants.Status.INVALID_CREDENTIALS)
                            .build());
            throw new UnauthorizedException(AuthMessage.INVALID_EMAIL_OR_PASSWORD);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException(AuthMessage.userAccountInactive(user.getStatus()));
        }

        String accessToken = UUID.randomUUID().toString();
        String refreshTokenString = UUID.randomUUID().toString();

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .userId(user.getId())
                        .tokenHash(hashPassword(refreshTokenString))
                        .expiresAt(
                                Instant.now().plus(AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY))
                        .revoked(false)
                        .build();

        refreshTokenRepository.save(refreshToken);

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.LOGIN_SUCCESS)
                        .userId(user.getId().toString())
                        .resource(AuthConstants.AuditResource.LOGIN)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresIn(Instant.now().plus(AuthConstants.TokenTtl.ACCESS_TOKEN_VALIDITY))
                .user(toUserResponse(user))
                .build();
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshTokenString) {
        if (tokenBlacklistService != null && accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklistToken(
                    accessToken, AuthConstants.TokenTtl.ACCESS_TOKEN_VALIDITY);
        }

        if (refreshTokenString != null && !refreshTokenString.isBlank()) {
            String hash = hashPassword(refreshTokenString);
            refreshTokenRepository
                    .findByTokenHash(hash)
                    .ifPresent(
                            rt -> {
                                rt.setRevoked(true);
                                refreshTokenRepository.save(rt);
                            });
        }

        UserContext context = UserContextHolder.getContext();
        String userId =
                (context != null && context.getUserId() != null)
                        ? context.getUserId()
                        : AuthConstants.UNKNOWN_USER_ID;

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.LOGOUT)
                        .userId(userId)
                        .resource(AuthConstants.AuditResource.LOGOUT)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        log.info("User [{}] logged out, token revoked", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .filter(u -> !u.isDeleted())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthOutboxConstants.AGGREGATE_TYPE_USER, userId));
        return toUserResponse(user);
    }

    @Override
    @Transactional
    public void softDeleteUser(UUID userId, String deletedBy) {
        User user =
                userRepository
                        .findById(userId)
                        .filter(u -> !u.isDeleted())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthOutboxConstants.AGGREGATE_TYPE_USER, userId));
        user.markDeleted(deletedBy);
        userRepository.save(user);

        outboxService.recordEvent(
                AuthOutboxConstants.AGGREGATE_TYPE_USER,
                userId.toString(),
                AuthOutboxConstants.EventType.USER_DELETED,
                Map.of(AuthOutboxConstants.PayloadKey.USER_ID, userId));
        log.info("User [{}] soft deleted by [{}]", userId, deletedBy);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String hashPassword(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance(AuthConstants.HASH_ALGORITHM_SHA256);
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    AuthConstants.HASH_ALGORITHM_SHA256 + " algorithm not available", e);
        }
    }

    private boolean verifyPassword(String raw, String hash) {
        return hashPassword(raw).equals(hash);
    }
}
