package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.dto.AccountResponseDTO;
import com.shareride.auth.dto.RegisterRequestDTO;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.impl.RegistrationServiceImpl;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.cache.DistributedLockService;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.util.UuidUtils;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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
                                .getBeanProvider(DistributedLockService.class),
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

        AccountResponseDTO expectedResponse =
                AccountResponseDTO.builder()
                        .accountId(generatedId)
                        .email("rider@shareride.com")
                        .phoneNumber(null)
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();
        when(accountManagementService.toAccountResponse(any())).thenReturn(expectedResponse);

        AccountResponseDTO response = registrationService.register(request);

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
        DistributedLockService lockService = mock(DistributedLockService.class);
        RolePermissionCacheService cacheService = mock(RolePermissionCacheService.class);

        when(lockService.executeWithLock(
                        any(String.class),
                        any(Duration.class),
                        ArgumentMatchers.<Supplier<AccountResponseDTO>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());

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
                        beanFactory.getBeanProvider(DistributedLockService.class),
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

        AccountResponseDTO expectedResponse = AccountResponseDTO.builder().build();
        when(accountManagementService.toAccountResponse(any())).thenReturn(expectedResponse);

        AccountResponseDTO response = lockedService.register(request);
        assertNotNull(response);
        verify(lockService)
                .executeWithLock(
                        any(String.class),
                        any(Duration.class),
                        ArgumentMatchers.<Supplier<AccountResponseDTO>>any());
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
