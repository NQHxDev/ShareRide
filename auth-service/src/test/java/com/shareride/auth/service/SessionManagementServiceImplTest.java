package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.Session;
import com.shareride.auth.domain.SessionStatus;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RefreshTokenData;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.repository.SessionRepository;
import com.shareride.auth.service.impl.SessionManagementServiceImpl;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionManagementServiceImplTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private RefreshTokenCacheService refreshTokenCacheService;
    @Mock private AuditLogService auditLogService;

    private SessionManagementService sessionManagementService;

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
        sessionManagementService =
                new SessionManagementServiceImpl(
                        sessionRepository, refreshTokenCacheService, auditLogService);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("createSession: Successfully creates and saves active session")
    void testCreateSessionSuccess() {
        UUID accountId = UUID.randomUUID();
        LoginRequest request =
                LoginRequest.builder()
                        .deviceId("dev-1")
                        .deviceName("iPhone 15")
                        .deviceType("MOBILE")
                        .location("Hanoi")
                        .build();

        when(sessionRepository.save(any(Session.class)))
                .thenAnswer(
                        invocation -> {
                            Session s = invocation.getArgument(0);
                            s.setId(UUID.randomUUID());
                            return s;
                        });

        Session session =
                sessionManagementService.createSession(
                        accountId, request, "127.0.0.1", "ShareRide/1.0");

        assertNotNull(session);
        assertEquals(accountId, session.getAccountId());
        assertEquals("dev-1", session.getDeviceId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    @DisplayName("refreshSession: Successfully validates and updates lastActiveAt")
    void testRefreshSessionSuccess() {
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = "valid-refresh-token";

        RefreshTokenData data =
                RefreshTokenData.builder().sessionId(sessionId).accountId(accountId).build();
        when(refreshTokenCacheService.getRefreshTokenData(token)).thenReturn(Optional.of(data));

        Session session =
                Session.builder()
                        .accountId(accountId)
                        .status(SessionStatus.ACTIVE)
                        .lastActiveAt(Instant.now().minusSeconds(100))
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
        session.setId(sessionId);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        Session refreshed = sessionManagementService.refreshSession(token, "192.168.1.1");

        assertNotNull(refreshed);
        assertEquals("192.168.1.1", refreshed.getIpAddress());
        verify(refreshTokenCacheService).revokeRefreshToken(token);
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("refreshSession: Fails when session is expired or revoked")
    void testRefreshSessionExpired() {
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = "expired-token";

        RefreshTokenData data =
                RefreshTokenData.builder().sessionId(sessionId).accountId(accountId).build();
        when(refreshTokenCacheService.getRefreshTokenData(token)).thenReturn(Optional.of(data));

        Session session =
                Session.builder()
                        .accountId(accountId)
                        .status(SessionStatus.EXPIRED)
                        .lastActiveAt(Instant.now().minusSeconds(1000))
                        .expiresAt(Instant.now().minusSeconds(100))
                        .build();
        session.setId(sessionId);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(
                UnauthorizedException.class,
                () -> sessionManagementService.refreshSession(token, "127.0.0.1"));
        verify(refreshTokenCacheService).revokeRefreshToken(token);
    }

    @Test
    @DisplayName("terminateSession: Revokes DB session and removes token from cache")
    void testTerminateSession() {
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = "logout-token";

        RefreshTokenData data =
                RefreshTokenData.builder().sessionId(sessionId).accountId(accountId).build();
        when(refreshTokenCacheService.getRefreshTokenData(token)).thenReturn(Optional.of(data));

        Session session =
                Session.builder().accountId(accountId).status(SessionStatus.ACTIVE).build();
        session.setId(sessionId);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        sessionManagementService.terminateSession(token);

        assertEquals(SessionStatus.REVOKED, session.getStatus());
        verify(sessionRepository).save(session);
        verify(refreshTokenCacheService).revokeRefreshToken(token);
    }

    @Test
    @DisplayName("revokeSession: Succeeds when self or admin revokes session")
    void testRevokeSessionSuccess() {
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Session session =
                Session.builder().accountId(accountId).status(SessionStatus.ACTIVE).build();
        session.setId(sessionId);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        sessionManagementService.revokeSession(sessionId, accountId);

        assertEquals(SessionStatus.REVOKED, session.getStatus());
        verify(sessionRepository).save(session);
        verify(refreshTokenCacheService).revokeSession(sessionId, accountId);
        verify(auditLogService).logSecurityEvent(any());
    }

    @Test
    @DisplayName("revokeSession: Throws ResourceNotFoundException when session not found")
    void testRevokeSessionNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> sessionManagementService.revokeSession(sessionId, UUID.randomUUID()));
    }

    @Test
    @DisplayName("getActiveSessions: Returns active sessions for authorized user")
    void testGetActiveSessionsSuccess() {
        UUID accountId = UUID.randomUUID();
        UserContextHolder.setContext(
                UserContext.builder()
                        .userId(accountId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build());

        Session session =
                Session.builder()
                        .accountId(accountId)
                        .deviceId("dev-1")
                        .deviceName("Android")
                        .deviceType("MOBILE")
                        .status(SessionStatus.ACTIVE)
                        .lastActiveAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
        session.setId(UUID.randomUUID());

        when(sessionRepository.findByAccountIdAndStatusOrderByLastActiveAtDesc(
                        accountId, SessionStatus.ACTIVE))
                .thenReturn(List.of(session));

        List<SessionResponse> responses = sessionManagementService.getActiveSessions(accountId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("dev-1", responses.get(0).getDeviceId());
    }

    @Test
    @DisplayName("getActiveSessions: Throws ForbiddenException when viewing other user's sessions")
    void testGetActiveSessionsForbidden() {
        UUID targetAccountId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserContextHolder.setContext(
                UserContext.builder()
                        .userId(otherUserId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build());

        assertThrows(
                ForbiddenException.class,
                () -> sessionManagementService.getActiveSessions(targetAccountId));
    }
}
