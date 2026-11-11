package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthSecurityAuditConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Session;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.JwtService;
import com.shareride.auth.service.RefreshTokenCacheService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link AuthService} xử lý các nghiệp vụ xác thực tài khoản cốt lõi, quản lý
 * chu kỳ sống của token, và đóng vai trò Facade điều phối các dịch vụ chuyên biệt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenCacheService refreshTokenCacheService;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final RegistrationService registrationService;
    private final SessionManagementService sessionManagementService;
    private final AccountManagementService accountManagementService;

    @Override
    @Transactional
    public AccountResponse register(RegisterRequest request) {
        return registrationService.register(request);
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
                                    auditLog(
                                            AuthSecurityAuditConstants.EventType.LOGIN_FAILURE,
                                            null,
                                            AuthConstants.AuditResource.LOGIN,
                                            AuthSecurityAuditConstants.Status.ACCOUNT_NOT_FOUND);
                                    return new UnauthorizedException(
                                            AuthMessage.INVALID_EMAIL_OR_PASSWORD);
                                });

        if (!PasswordUtils.verifyPassword(request.getPassword(), account.getPasswordHash())) {
            auditLog(
                    AuthSecurityAuditConstants.EventType.LOGIN_FAILURE,
                    account.getId().toString(),
                    AuthConstants.AuditResource.LOGIN,
                    AuthSecurityAuditConstants.Status.INVALID_CREDENTIALS);
            throw new UnauthorizedException(AuthMessage.INVALID_EMAIL_OR_PASSWORD);
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException(AuthMessage.accountInactive(account.getStatus()));
        }

        Session session =
                sessionManagementService.createSession(
                        account.getId(), request, ipAddress, userAgent);
        String accessToken = jwtService.generateAccessToken(account, session);
        String refreshToken = UuidUtils.v4AsString();

        refreshTokenCacheService.saveRefreshToken(
                session.getId(),
                account.getId(),
                refreshToken,
                AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY);

        auditLog(
                AuthSecurityAuditConstants.EventType.LOGIN_SUCCESS,
                account.getId().toString(),
                AuthConstants.AuditResource.LOGIN,
                AuthSecurityAuditConstants.Status.SUCCESS);

        return buildAuthResponse(accessToken, refreshToken, session.getId(), account);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken, String ipAddress, String userAgent) {
        Session session = sessionManagementService.refreshSession(refreshToken, ipAddress);
        Account account = accountManagementService.getActiveAccount(session.getAccountId());

        String newAccessToken = jwtService.generateAccessToken(account, session);
        String newRefreshToken = UuidUtils.v4AsString();

        refreshTokenCacheService.saveRefreshToken(
                session.getId(),
                account.getId(),
                newRefreshToken,
                AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY);

        auditLog(
                AuthSecurityAuditConstants.EventType.TOKEN_REFRESH,
                account.getId().toString(),
                AuthConstants.AuditResource.REFRESH,
                AuthSecurityAuditConstants.Status.SUCCESS);

        return buildAuthResponse(newAccessToken, newRefreshToken, session.getId(), account);
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            jwtService.revokeToken(accessToken);
        }
        sessionManagementService.terminateSession(refreshToken);

        UserContext context = UserContextHolder.getContext();
        String accountId =
                (context != null && context.getUserId() != null)
                        ? context.getUserId()
                        : AuthConstants.UNKNOWN_ACCOUNT_ID;

        auditLog(
                AuthSecurityAuditConstants.EventType.LOGOUT,
                accountId,
                AuthConstants.AuditResource.LOGOUT,
                AuthSecurityAuditConstants.Status.SUCCESS);
        log.info("Account [{}] logged out, session revoked", accountId);
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId, UUID currentAccountId) {
        sessionManagementService.revokeSession(sessionId, currentAccountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(UUID accountId) {
        return sessionManagementService.getActiveSessions(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        return accountManagementService.getAccountById(accountId);
    }

    @Override
    @Transactional
    public void softDeleteAccount(UUID accountId, String deletedBy) {
        accountManagementService.softDeleteAccount(accountId, deletedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getJwks() {
        return jwtService.getJwks();
    }

    private AuthResponse buildAuthResponse(
            String accessToken, String refreshToken, UUID sessionId, Account account) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresIn(Instant.now().plus(jwtService.getAccessTokenTtl()))
                .sessionId(sessionId)
                .account(accountManagementService.toAccountResponse(account))
                .build();
    }

    private void auditLog(String eventType, String userId, String resource, String status) {
        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(eventType)
                        .userId(userId)
                        .resource(resource)
                        .status(status)
                        .build());
    }
}
