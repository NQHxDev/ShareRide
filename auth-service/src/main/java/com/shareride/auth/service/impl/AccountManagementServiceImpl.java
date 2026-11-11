package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.RolePermissionCacheService;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.outbox.OutboxService;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link AccountManagementService} xử lý truy vấn tài khoản, xóa mềm và ánh xạ
 * DTO bảo mật.
 */
@Slf4j
@Service
public class AccountManagementServiceImpl implements AccountManagementService {

    private final AccountRepository accountRepository;
    private final SessionManagementService sessionManagementService;
    private final OutboxService outboxService;
    private final RolePermissionCacheService rolePermissionCacheService;

    @Autowired
    public AccountManagementServiceImpl(
            AccountRepository accountRepository,
            SessionManagementService sessionManagementService,
            OutboxService outboxService,
            ObjectProvider<RolePermissionCacheService> rolePermissionCacheServiceProvider) {
        this.accountRepository = accountRepository;
        this.sessionManagementService = sessionManagementService;
        this.outboxService = outboxService;
        this.rolePermissionCacheService =
                rolePermissionCacheServiceProvider != null
                        ? rolePermissionCacheServiceProvider.getIfAvailable()
                        : null;
    }

    public AccountManagementServiceImpl(
            AccountRepository accountRepository,
            SessionManagementService sessionManagementService,
            OutboxService outboxService) {
        this(accountRepository, sessionManagementService, outboxService, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        UserContextHolder.requireSelfOrAdmin(accountId);
        Account account =
                accountRepository
                        .findActiveByIdWithRoles(accountId)
                        .or(() -> accountRepository.findById(accountId).filter(a -> !a.isDeleted()))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                                                accountId));
        return toAccountResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Account getActiveAccount(UUID accountId) {
        return accountRepository
                .findActiveByIdWithRoles(accountId)
                .or(() -> accountRepository.findById(accountId).filter(a -> !a.isDeleted()))
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(
                        () ->
                                new UnauthorizedException(
                                        AuthMessage.accountInactive("ACCOUNT_DISABLED")));
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

        sessionManagementService.revokeAllSessions(accountId);

        outboxService.recordEvent(
                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                accountId.toString(),
                AuthOutboxConstants.EventType.ACCOUNT_DELETED,
                Map.of(AuthOutboxConstants.PayloadKey.ACCOUNT_ID, accountId));
        log.info("Account [{}] soft deleted by [{}]", accountId, deletedBy);
    }

    @Override
    public AccountResponse toAccountResponse(Account account) {
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

        Set<String> permissions;
        if (rolePermissionCacheService != null && !roleCodes.isEmpty()) {
            Set<RoleCode> roleCodeEnums =
                    account.getRoles().stream()
                            .filter(Objects::nonNull)
                            .map(role -> role.getRoleCode())
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            permissions = rolePermissionCacheService.getPermissionsByRoleCodes(roleCodeEnums);
        } else {
            permissions =
                    account.getRoles() != null
                            ? account.getRoles().stream()
                                    .filter(Objects::nonNull)
                                    .filter(r -> r.getPermissions() != null)
                                    .flatMap(r -> r.getPermissions().stream())
                                    .filter(Objects::nonNull)
                                    .map(
                                            permission ->
                                                    permission.getPermissionCode() != null
                                                            ? permission
                                                                    .getPermissionCode()
                                                                    .getValue()
                                                            : null)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet())
                            : Set.of();
        }

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
}
