package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthSecurityAuditConstants;
import com.shareride.auth.domain.Session;
import com.shareride.auth.domain.SessionStatus;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RefreshTokenData;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.repository.SessionRepository;
import com.shareride.auth.service.RefreshTokenCacheService;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link SessionManagementService} xử lý các nghiệp vụ truy vấn, khởi tạo, thu
 * hồi và quản lý vòng đời phiên làm việc đa thiết bị.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagementServiceImpl implements SessionManagementService {

    private final SessionRepository sessionRepository;
    private final RefreshTokenCacheService refreshTokenCacheService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public Session createSession(
            UUID accountId, LoginRequest request, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY);

        Session session =
                Session.builder()
                        .accountId(accountId)
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

        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public Session refreshSession(String refreshToken, String ipAddress) {
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

        // Token Rotation: Thu hồi token cũ
        refreshTokenCacheService.revokeRefreshToken(refreshToken);

        session.setLastActiveAt(Instant.now());
        if (ipAddress != null && !ipAddress.isBlank()) {
            session.setIpAddress(ipAddress);
        }
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void terminateSession(String refreshToken) {
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
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId, UUID currentAccountId) {
        Session session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (UserContextHolder.isAuthenticated()) {
            UserContextHolder.requireSelfOrAdmin(session.getAccountId());
        } else if (currentAccountId != null && !session.getAccountId().equals(currentAccountId)) {
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
        UserContextHolder.requireSelfOrAdmin(accountId);
        return sessionRepository
                .findByAccountIdAndStatusOrderByLastActiveAtDesc(accountId, SessionStatus.ACTIVE)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional
    public void revokeAllSessions(UUID accountId) {
        sessionRepository
                .findByAccountIdOrderByLastActiveAtDesc(accountId)
                .forEach(
                        s -> {
                            s.setStatus(SessionStatus.REVOKED);
                            sessionRepository.save(s);
                        });
        refreshTokenCacheService.revokeAllSessions(accountId);
        log.info("All sessions revoked for account [{}]", accountId);
    }

    @Override
    public SessionResponse toSessionResponse(Session session) {
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
