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
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.impl.RegistrationServiceImpl;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.outbox.OutboxService;
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
                        eventPublisher);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Register: Password must be hashed using Argon2id format ($argon2id$v=19$...)")
    void testRegisterWithArgon2id() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("rider@shareride.com")
                        .password("SuperSecretPass123!")
                        .phoneNumber("+84988111222")
                        .roleCode("ROLE_RIDER")
                        .fullName("Nguyen Van A")
                        .build();

        Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).roleName("Rider").build();
        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        when(accountRepository.existsByEmail("rider@shareride.com")).thenReturn(false);
        when(accountRepository.existsByPhoneNumber("+84988111222")).thenReturn(false);
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));

        UUID generatedId = UUID.randomUUID();
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
                        .phoneNumber("+84988111222")
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
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("existing@shareride.com")
                        .password("Pass123456")
                        .phoneNumber("+84988111222")
                        .build();

        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        when(accountRepository.existsByEmail("existing@shareride.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> registrationService.register(request));
    }

    @Test
    @DisplayName("Register: Fails when phone number already exists")
    void testRegisterPhoneAlreadyExists() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("unique@shareride.com")
                        .password("Pass123456")
                        .phoneNumber("+84988111222")
                        .build();

        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        when(accountRepository.existsByEmail("unique@shareride.com")).thenReturn(false);
        when(accountRepository.existsByPhoneNumber("+84988111222")).thenReturn(true);

        assertThrows(ConflictException.class, () -> registrationService.register(request));
    }

    @Test
    @DisplayName("Register: Uses DistributedLockService when available and handles null fullName")
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

        Role driverRole = Role.builder().roleCode(RoleCode.ROLE_DRIVER).build();
        when(cacheService.getRoleByCode(RoleCode.ROLE_DRIVER)).thenReturn(Optional.of(driverRole));

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
                        eventPublisher);

        RegisterRequest request =
                RegisterRequest.builder()
                        .email("driver.locked@shareride.com")
                        .password("SecurePass123")
                        .phoneNumber("+84988999888")
                        .roleCode("ROLE_DRIVER")
                        .fullName(null)
                        .build();

        when(emailBloomFilterService.mightExist(any())).thenReturn(true);
        when(accountRepository.existsByEmail("driver.locked@shareride.com")).thenReturn(false);
        when(accountRepository.existsByPhoneNumber("+84988999888")).thenReturn(false);
        when(accountRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Account a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
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
                        eventPublisher);
        assertNotNull(simpleService);
    }
}
