package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthSecurityAuditConstants;
import com.shareride.auth.constant.AuthVerificationConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequestDTO;
import com.shareride.auth.event.AccountRegisteredEvent;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.auth.service.RolePermissionCacheService;
import com.shareride.auth.util.AlphanumericCodeGenerator;
import com.shareride.auth.util.HashingUtils;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.cache.DistributedLockService;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
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
    private final AccountVerificationRepository accountVerificationRepository;
    private final AuthEventPublisher authEventPublisher;

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
            ApplicationEventPublisher eventPublisher,
            AccountVerificationRepository accountVerificationRepository,
            AuthEventPublisher authEventPublisher) {
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
        this.accountVerificationRepository = accountVerificationRepository;
        this.authEventPublisher = authEventPublisher;
    }

    public RegistrationServiceImpl(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            OutboxService outboxService,
            ObjectProvider<DistributedLockService> distributedLockServiceProvider,
            AuditLogService auditLogService,
            AccountManagementService accountManagementService,
            EmailBloomFilterService emailBloomFilterService,
            ApplicationEventPublisher eventPublisher,
            AccountVerificationRepository accountVerificationRepository,
            AuthEventPublisher authEventPublisher) {
        this(
                accountRepository,
                roleRepository,
                null,
                outboxService,
                distributedLockServiceProvider,
                auditLogService,
                accountManagementService,
                emailBloomFilterService,
                eventPublisher,
                accountVerificationRepository,
                authEventPublisher);
    }

    @Override
    @Transactional
    public AccountResponse register(RegisterRequestDTO request) {
        // Validate password confirmation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

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

    private AccountResponse doRegister(RegisterRequestDTO request, String normalizedEmail) {
        // FR-013: Check if email already exists with PENDING_VERIFICATION status
        boolean mightExist = emailBloomFilterService.mightExist(normalizedEmail);
        if (mightExist) {
            Optional<Account> existingAccount = accountRepository.findByEmail(normalizedEmail);
            if (existingAccount.isPresent()) {
                if (existingAccount.get().getStatus() == AccountStatus.PENDING_VERIFICATION) {
                    throw new ConflictException(
                            AuthResponseCode.EMAIL_PENDING_VERIFICATION,
                            AuthMessage.EMAIL_PENDING_VERIFICATION);
                }
                throw new ConflictException(
                        AuthResponseCode.EMAIL_ALREADY_EXISTS,
                        AuthMessage.emailAlreadyExists(normalizedEmail));
            }
        }

        // Default role is ROLE_RIDER
        Role role = resolveTargetRole(null);

        Account account =
                accountRepository.save(
                        Account.builder()
                                .email(normalizedEmail)
                                .phoneNumber(null)
                                .passwordHash(PasswordUtils.hashPassword(request.getPassword()))
                                .status(AccountStatus.PENDING_VERIFICATION)
                                .roles(role != null ? Set.of(role) : Set.of())
                                .build());

        // Publish event for Bloom filter sync
        eventPublisher.publishEvent(new AccountRegisteredEvent(account.getEmail()));

        // FR-002: Generate 6-character alphanumeric verification code
        String verificationCode = AlphanumericCodeGenerator.generate();
        String codeHash = HashingUtils.sha256Hex(verificationCode);

        // FR-002, FR-003: Persist verification challenge with 15-minute expiry
        AccountVerification verification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(codeHash)
                        .expiresAt(Instant.now().plus(AuthVerificationConstants.EXPIRY_DURATION))
                        .failedAttempts(0)
                        .status(VerificationStatus.PENDING)
                        .build();
        accountVerificationRepository.save(verification);

        // FR-004: Dispatch ACCOUNT_VERIFICATION event via Outbox
        // Use email prefix as default username since fullName is no longer collected during
        // registration
        String userName = account.getEmail().split("@")[0];

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userName", userName);
        parameters.put("verificationCode", verificationCode);
        parameters.put("expiresInMinutes", 15);

        Map<String, Object> verificationPayload = new HashMap<>();
        verificationPayload.put("eventId", UuidUtils.v7().toString());
        verificationPayload.put("recipientEmail", account.getEmail());
        verificationPayload.put("notificationType", "ACCOUNT_VERIFICATION");
        verificationPayload.put("locale", "vi-VN");
        verificationPayload.put("parameters", parameters);

        authEventPublisher.publishAccountEvent(
                account.getId().toString(), "ACCOUNT_VERIFICATION", verificationPayload);

        List<String> roleCodes =
                account.getRoles() != null
                        ? account.getRoles().stream()
                                .map(r -> r.getRoleCode() != null ? r.getRoleCode().name() : null)
                                .filter(Objects::nonNull)
                                .toList()
                        : List.of();

        Map<String, Object> payload = new HashMap<>();
        payload.put(AuthOutboxConstants.PayloadKey.ACCOUNT_ID, account.getId());
        payload.put(AuthOutboxConstants.PayloadKey.EMAIL, account.getEmail());
        payload.put(AuthOutboxConstants.PayloadKey.ROLES, roleCodes);
        // Phone number and full name are no longer part of initial registration

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

        log.info(
                "Registered account [{}] with PENDING_VERIFICATION status and dispatched"
                        + " verification email",
                account.getId());
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
