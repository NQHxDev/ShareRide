package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequestDTO;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.impl.RegistrationServiceImpl;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.util.UuidUtils;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogService auditLogService;
    @Mock private AccountManagementService accountManagementService;
    @Mock private EmailBloomFilterService emailBloomFilterService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountVerificationRepository accountVerificationRepository;
    @Mock private AuthEventPublisher authEventPublisher;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
        PasswordUtils.setSecret("test_argon2id_secret_pepper_12345");
        registrationService =
                new RegistrationServiceImpl(
                        accountRepository,
                        roleRepository,
                        outboxService,
                        new StaticListableBeanFactory()
                                .getBeanProvider(
                                        com.shareride.common.cache.DistributedLockService.class),
                        auditLogService,
                        accountManagementService,
                        emailBloomFilterService,
                        eventPublisher,
                        accountVerificationRepository,
                        authEventPublisher);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Register: Password must be hashed using Argon2id format ($argon2id$v=19$...)")
    void testRegisterWithArgon2id() {
        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email("rider@shareride.com")
                        .password("SuperSecretPass123!")
                        .confirmPassword("SuperSecretPass123!")
                        .build();

        Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).roleName("Rider").build();
        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        when(accountRepository.findByEmail("rider@shareride.com")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));

        UUID generatedId = UuidUtils.v7();
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture()))
                .thenAnswer(
                        invocation -> {
                            Account a = invocation.getArgument(0);
                            a.setId(generatedId);
                            return a;
                        });

        AccountResponse expectedResponse =
                AccountResponse.builder()
                        .accountId(generatedId)
                        .email("rider@shareride.com")
                        .phoneNumber(null)
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();
        when(accountManagementService.toAccountResponse(any())).thenReturn(expectedResponse);

        AccountResponse response = registrationService.register(request);

        assertNotNull(response);
        Account savedAccount = accountCaptor.getValue();
        assertNotNull(savedAccount.getPasswordHash());
        assertTrue(
                savedAccount.getPasswordHash().startsWith("$argon2id$v=19$m=16384,t=2,p=1$"),
                "Password must be hashed with Argon2id parameters");
        assertTrue(
                PasswordUtils.verifyPassword("SuperSecretPass123!", savedAccount.getPasswordHash()),
                "Argon2id password encoder must verify raw password correctly");
        verify(outboxService).recordEvent(any(), any(), any(), any());
        verify(auditLogService).logSecurityEvent(any());
    }

    @Test
    @DisplayName("Register: Fails when email already exists")
    void testRegisterEmailAlreadyExists() {
        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email("existing@shareride.com")
                        .password("Pass123456")
                        .confirmPassword("Pass123456")
                        .build();

        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        Account existingAccount =
                Account.builder()
                        .email("existing@shareride.com")
                        .status(AccountStatus.ACTIVE)
                        .build();
        existingAccount.setId(UuidUtils.v7());
        when(accountRepository.findByEmail("existing@shareride.com"))
                .thenReturn(Optional.of(existingAccount));

        assertThrows(ConflictException.class, () -> registrationService.register(request));
    }

    @Test
    @DisplayName("Register: Fails when passwords do not match")
    void testRegisterPhoneAlreadyExists() {
        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email("unique@shareride.com")
                        .password("Pass123456")
                        .confirmPassword("DifferentPassword")
                        .build();

        assertThrows(IllegalArgumentException.class, () -> registrationService.register(request));
    }

    @Test
    @DisplayName("Register: Uses DistributedLockService when available")
    void testRegisterWithDistributedLockAndNullFullName() {
        com.shareride.common.cache.DistributedLockService lockService =
                org.mockito.Mockito.mock(com.shareride.common.cache.DistributedLockService.class);
        RolePermissionCacheService cacheService =
                org.mockito.Mockito.mock(RolePermissionCacheService.class);

        when(lockService.executeWithLock(
                        any(String.class),
                        any(java.time.Duration.class),
                        org.mockito.ArgumentMatchers
                                .<java.util.function.Supplier<AccountResponse>>any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).build();
        when(cacheService.getRoleByCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("distributedLockService", lockService);
        beanFactory.addBean("rolePermissionCacheService", cacheService);

        RegistrationService lockedService =
                new RegistrationServiceImpl(
                        accountRepository,
                        roleRepository,
                        beanFactory.getBeanProvider(RolePermissionCacheService.class),
                        outboxService,
                        beanFactory.getBeanProvider(
                                com.shareride.common.cache.DistributedLockService.class),
                        auditLogService,
                        accountManagementService,
                        emailBloomFilterService,
                        eventPublisher,
                        accountVerificationRepository,
                        authEventPublisher);

        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email("driver.locked@shareride.com")
                        .password("SecurePass123")
                        .confirmPassword("SecurePass123")
                        .build();

        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        when(accountRepository.findByEmail("driver.locked@shareride.com"))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Account a = inv.getArgument(0);
                            a.setId(UuidUtils.v7());
                            return a;
                        });

        AccountResponse expectedResponse = AccountResponse.builder().build();
        when(accountManagementService.toAccountResponse(any())).thenReturn(expectedResponse);

        AccountResponse response = lockedService.register(request);
        assertNotNull(response);
        verify(lockService)
                .executeWithLock(
                        any(String.class),
                        any(java.time.Duration.class),
                        org.mockito.ArgumentMatchers
                                .<java.util.function.Supplier<AccountResponse>>any());
    }

    @Test
    @DisplayName("Register: Secondary constructor without cache provider")
    void testSecondaryConstructor() {
        RegistrationService simpleService =
                new RegistrationServiceImpl(
                        accountRepository,
                        roleRepository,
                        outboxService,
                        null,
                        auditLogService,
                        accountManagementService,
                        emailBloomFilterService,
                        eventPublisher,
                        accountVerificationRepository,
                        authEventPublisher);
        assertNotNull(simpleService);
    }
}
