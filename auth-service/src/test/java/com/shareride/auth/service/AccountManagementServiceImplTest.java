package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.PermissionCode;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.impl.AccountManagementServiceImpl;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.outbox.OutboxService;
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
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private SessionManagementService sessionManagementService;
    @Mock private OutboxService outboxService;

    private AccountManagementService accountManagementService;

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
        accountManagementService =
                new AccountManagementServiceImpl(
                        accountRepository,
                        sessionManagementService,
                        outboxService,
                        new StaticListableBeanFactory()
                                .getBeanProvider(RolePermissionCacheService.class));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("getAccountById: Throws UnauthorizedException when unauthenticated")
    void testGetAccountByIdUnauthenticated() {
        UUID accountId = UUID.randomUUID();
        assertThrows(
                UnauthorizedException.class,
                () -> accountManagementService.getAccountById(accountId));
    }

    @Test
    @DisplayName(
            "getAccountById: Throws ForbiddenException when viewing another account without ADMIN")
    void testGetAccountByIdForbidden() {
        UUID targetId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        UserContextHolder.setContext(
                UserContext.builder()
                        .userId(currentUserId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build());

        assertThrows(
                ForbiddenException.class, () -> accountManagementService.getAccountById(targetId));
    }

    @Test
    @DisplayName("getAccountById: Succeeds when viewing own account")
    void testGetAccountByIdOwnAccount() {
        UUID accountId = UUID.randomUUID();
        Role role =
                Role.builder()
                        .roleCode(RoleCode.ROLE_RIDER)
                        .permissions(
                                Set.of(
                                        Permission.builder()
                                                .permissionCode(PermissionCode.RIDE_CREATE)
                                                .build()))
                        .build();

        Account account =
                Account.builder()
                        .email("self@shareride.com")
                        .phoneNumber("+84988777666")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of(role))
                        .build();
        account.setId(accountId);

        UserContextHolder.setContext(
                UserContext.builder()
                        .userId(accountId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build());

        when(accountRepository.findActiveByIdWithRoles(accountId)).thenReturn(Optional.of(account));

        AccountResponse response = accountManagementService.getAccountById(accountId);

        assertNotNull(response);
        assertEquals(accountId, response.getAccountId());
        assertEquals("self@shareride.com", response.getEmail());
        assertTrue(response.getRoles().contains("ROLE_RIDER"));
        assertTrue(response.getPermissions().contains("ride:create"));
    }

    @Test
    @DisplayName("getAccountById: Throws ResourceNotFoundException when account not found")
    void testGetAccountByIdNotFound() {
        UUID accountId = UUID.randomUUID();
        UserContextHolder.setContext(
                UserContext.builder()
                        .userId(accountId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build());

        when(accountRepository.findActiveByIdWithRoles(accountId)).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountManagementService.getAccountById(accountId));
    }

    @Test
    @DisplayName("getActiveAccount: Returns active account")
    void testGetActiveAccountSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account =
                Account.builder()
                        .email("active@shareride.com")
                        .status(AccountStatus.ACTIVE)
                        .build();
        account.setId(accountId);

        when(accountRepository.findActiveByIdWithRoles(accountId)).thenReturn(Optional.of(account));

        Account result = accountManagementService.getActiveAccount(accountId);
        assertNotNull(result);
        assertEquals(accountId, result.getId());
    }

    @Test
    @DisplayName("getActiveAccount: Throws UnauthorizedException when account disabled")
    void testGetActiveAccountDisabled() {
        UUID accountId = UUID.randomUUID();
        Account account =
                Account.builder()
                        .email("blocked@shareride.com")
                        .status(AccountStatus.INACTIVE)
                        .build();
        account.setId(accountId);

        when(accountRepository.findActiveByIdWithRoles(accountId)).thenReturn(Optional.of(account));

        assertThrows(
                UnauthorizedException.class,
                () -> accountManagementService.getActiveAccount(accountId));
    }

    @Test
    @DisplayName(
            "softDeleteAccount: Marks account deleted, revokes all sessions, and emits outbox"
                    + " event")
    void testSoftDeleteAccount() {
        UUID accountId = UUID.randomUUID();
        Account account =
                Account.builder()
                        .email("delete.me@shareride.com")
                        .status(AccountStatus.ACTIVE)
                        .build();
        account.setId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        accountManagementService.softDeleteAccount(accountId, "admin-user");

        assertTrue(account.isDeleted());
        assertEquals("admin-user", account.getDeletedBy());
        verify(accountRepository).save(account);
        verify(sessionManagementService).revokeAllSessions(accountId);
        verify(outboxService).recordEvent(any(), any(), any(), any());
    }
}
