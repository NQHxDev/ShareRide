package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.service.impl.RegistrationServiceImpl;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.util.UuidUtils;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplVerificationTest {

    @Mock private AccountRepository accountRepository;

    @Mock private AccountVerificationRepository accountVerificationRepository;

    @Mock private RoleRepository roleRepository;

    @Mock private OutboxService outboxService;

    @Mock private AuditLogService auditLogService;

    @Mock private AccountManagementService accountManagementService;

    @Mock private EmailBloomFilterService emailBloomFilterService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private AuthEventPublisher authEventPublisher;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService =
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
    }

    @Test
    void register_ShouldCreateAccountWithPendingVerificationStatus() {
        final RegisterRequest request =
                RegisterRequest.builder()
                        .email("newuser@example.com")
                        .password("SecurePass123!")
                        .phoneNumber("+84123456789")
                        .fullName("Nguyen Van A")
                        .build();

        final Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).build();

        final Account savedAccount =
                Account.builder()
                        .email("newuser@example.com")
                        .phoneNumber("+84123456789")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .roles(Set.of(riderRole))
                        .build();
        savedAccount.setId(UuidUtils.v7());

        when(emailBloomFilterService.mightExist(anyString())).thenReturn(false);
        when(accountRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(accountManagementService.toAccountResponse(any(Account.class)))
                .thenReturn(new AccountResponse());

        final AccountResponse response = registrationService.register(request);

        final ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());

        final Account capturedAccount = accountCaptor.getValue();
        assertEquals(AccountStatus.PENDING_VERIFICATION, capturedAccount.getStatus());
        assertNotNull(response);
    }

    @Test
    void register_ShouldGenerateAndStoreVerificationCodeHash() {
        final RegisterRequest request =
                RegisterRequest.builder()
                        .email("user@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84987654321")
                        .build();

        final Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).build();
        final Account savedAccount =
                Account.builder()
                        .email("user@example.com")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .roles(Set.of(riderRole))
                        .build();
        savedAccount.setId(UuidUtils.v7());

        when(emailBloomFilterService.mightExist(anyString())).thenReturn(false);
        when(accountRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(accountManagementService.toAccountResponse(any(Account.class)))
                .thenReturn(new AccountResponse());

        registrationService.register(request);

        final ArgumentCaptor<AccountVerification> verificationCaptor =
                ArgumentCaptor.forClass(AccountVerification.class);
        verify(accountVerificationRepository, times(1)).save(verificationCaptor.capture());

        final AccountVerification capturedVerification = verificationCaptor.getValue();
        assertEquals(savedAccount.getId(), capturedVerification.getAccountId());
        assertEquals(VerificationStatus.PENDING, capturedVerification.getStatus());
        assertEquals(0, capturedVerification.getFailedAttempts());
        assertNotNull(capturedVerification.getVerificationCodeHash());
        assertEquals(64, capturedVerification.getVerificationCodeHash().length());
        assertNotNull(capturedVerification.getExpiresAt());
    }

    @Test
    void register_ShouldPublishAccountVerificationEvent() {
        final RegisterRequest request =
                RegisterRequest.builder()
                        .email("test@example.com")
                        .password("Password123!")
                        .phoneNumber("+84111222333")
                        .fullName("Test User")
                        .build();

        final Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).build();
        final Account savedAccount =
                Account.builder()
                        .email("test@example.com")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .roles(Set.of(riderRole))
                        .build();
        savedAccount.setId(UuidUtils.v7());

        when(emailBloomFilterService.mightExist(anyString())).thenReturn(false);
        when(accountRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(accountManagementService.toAccountResponse(any(Account.class)))
                .thenReturn(new AccountResponse());

        registrationService.register(request);

        verify(authEventPublisher, times(1))
                .publishAccountEvent(
                        eq(savedAccount.getId().toString()), eq("ACCOUNT_VERIFICATION"), any());
    }

    @Test
    void register_ShouldThrowConflictExceptionWhenEmailPendingVerification() {
        final RegisterRequest request =
                RegisterRequest.builder()
                        .email("pending@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84999888777")
                        .build();

        final Account existingAccount =
                Account.builder()
                        .email("pending@example.com")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .build();

        when(emailBloomFilterService.mightExist(anyString())).thenReturn(true);
        when(accountRepository.findByEmail("pending@example.com"))
                .thenReturn(Optional.of(existingAccount));

        final ConflictException exception =
                assertThrows(ConflictException.class, () -> registrationService.register(request));

        assertEquals(AuthResponseCode.EMAIL_PENDING_VERIFICATION, exception.getCode());
    }
}
