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
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.event.AccountRegisteredEvent;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.auth.service.RolePermissionCacheService;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.cache.DistributedLockService;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import com.shareride.common.outbox.OutboxService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai chuẩn của {@link RegistrationService} xử lý các thao tác đăng ký người dùng, kiểm tra
 * tính duy nhất, khóa phân tán và ghi nhận sự kiện Dual-write Outbox.
 */
@Slf4j
@Service
public class RegistrationServiceImpl implements RegistrationService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionCacheService rolePermissionCacheService;
    private final OutboxService outboxService;
    private final DistributedLockService distributedLockService;
    private final AuditLogService auditLogService;
    private final AccountManagementService accountManagementService;
    private final EmailBloomFilterService emailBloomFilterService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RegistrationServiceImpl(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            ObjectProvider<RolePermissionCacheService> rolePermissionCacheServiceProvider,
            OutboxService outboxService,
            ObjectProvider<DistributedLockService> distributedLockServiceProvider,
            AuditLogService auditLogService,
            AccountManagementService accountManagementService,
            EmailBloomFilterService emailBloomFilterService,
            ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionCacheService =
                rolePermissionCacheServiceProvider != null
                        ? rolePermissionCacheServiceProvider.getIfAvailable()
                        : null;
        this.outboxService = outboxService;
        this.distributedLockService =
                distributedLockServiceProvider != null
                        ? distributedLockServiceProvider.getIfAvailable()
                        : null;
        this.auditLogService = auditLogService;
        this.accountManagementService = accountManagementService;
        this.emailBloomFilterService = emailBloomFilterService;
        this.eventPublisher = eventPublisher;
    }

    public RegistrationServiceImpl(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            OutboxService outboxService,
            ObjectProvider<DistributedLockService> distributedLockServiceProvider,
            AuditLogService auditLogService,
            AccountManagementService accountManagementService,
            EmailBloomFilterService emailBloomFilterService,
            ApplicationEventPublisher eventPublisher) {
        this(
                accountRepository,
                roleRepository,
                null,
                outboxService,
                distributedLockServiceProvider,
                auditLogService,
                accountManagementService,
                emailBloomFilterService,
                eventPublisher);
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

        // Bloom filter fast path: skip DB query if email definitely does not exist
        boolean mightExist = emailBloomFilterService.mightExist(normalizedEmail);
        if (mightExist) {
            // Bloom filter hit: confirm with PostgreSQL (may be false positive)
            if (accountRepository.existsByEmail(normalizedEmail)) {
                throw new ConflictException(
                        AuthResponseCode.EMAIL_ALREADY_EXISTS,
                        AuthMessage.emailAlreadyExists(normalizedEmail));
            }
        }
        // If Bloom filter returns false, skip DB query (email definitely does not exist)

        if (accountRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new ConflictException(
                    AuthResponseCode.PHONE_ALREADY_EXISTS,
                    AuthMessage.phoneNumberAlreadyExists(normalizedPhone));
        }

        Role role = resolveTargetRole(request.getRoleCode());
        Account account =
                accountRepository.save(
                        Account.builder()
                                .email(normalizedEmail)
                                .phoneNumber(normalizedPhone)
                                .passwordHash(PasswordUtils.hashPassword(request.getPassword()))
                                .status(AccountStatus.ACTIVE)
                                .roles(role != null ? Set.of(role) : Set.of())
                                .build());

        // Publish event for Bloom filter sync (AFTER_COMMIT listener will add email)
        eventPublisher.publishEvent(new AccountRegisteredEvent(account.getEmail()));

        List<String> roleCodes =
                account.getRoles() != null
                        ? account.getRoles().stream()
                                .map(r -> r.getRoleCode() != null ? r.getRoleCode().name() : null)
                                .filter(Objects::nonNull)
                                .toList()
                        : List.of();

        Map<String, Object> payload =
                new HashMap<>(
                        Map.of(
                                AuthOutboxConstants.PayloadKey.ACCOUNT_ID,
                                account.getId(),
                                AuthOutboxConstants.PayloadKey.EMAIL,
                                account.getEmail(),
                                AuthOutboxConstants.PayloadKey.PHONE_NUMBER,
                                account.getPhoneNumber(),
                                AuthOutboxConstants.PayloadKey.ROLES,
                                roleCodes));
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            payload.put(AuthOutboxConstants.PayloadKey.FULL_NAME, request.getFullName().trim());
        }

        outboxService.recordEvent(
                AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT,
                account.getId().toString(),
                AuthOutboxConstants.EventType.ACCOUNT_REGISTERED,
                payload);

        auditLogService.logSecurityEvent(
                SecurityAuditEvent.builder()
                        .eventType(AuthSecurityAuditConstants.EventType.ACCOUNT_REGISTRATION)
                        .userId(account.getId().toString())
                        .resource(AuthConstants.AuditResource.REGISTER)
                        .status(AuthSecurityAuditConstants.Status.SUCCESS)
                        .build());

        log.info("Registered account [{}] with roles {}", account.getId(), roleCodes);
        return accountManagementService.toAccountResponse(account);
    }

    private Role resolveTargetRole(String roleCodeStr) {
        RoleCode target =
                Optional.ofNullable(RoleCode.fromString(roleCodeStr)).orElse(RoleCode.ROLE_RIDER);
        if (rolePermissionCacheService != null) {
            Optional<Role> cached = rolePermissionCacheService.getRoleByCode(target);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        return roleRepository
                .findByRoleCode(target)
                .orElseGet(() -> roleRepository.findByRoleCode(RoleCode.ROLE_RIDER).orElse(null));
    }
}
