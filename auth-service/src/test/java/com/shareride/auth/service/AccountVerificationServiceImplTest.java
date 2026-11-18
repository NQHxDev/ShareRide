package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.VerifyEmailRequest;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.service.impl.AccountVerificationServiceImpl;
import com.shareride.auth.util.HashingUtils;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class AccountVerificationServiceImplTest {

    @Mock private AccountRepository accountRepository;

    @Mock private AccountVerificationRepository accountVerificationRepository;

    @Mock private AccountManagementService accountManagementService;

    @Mock private AuthEventPublisher authEventPublisher;

    @Mock private RedisTemplate<String, Object> redisTemplate;

    private AccountVerificationService accountVerificationService;

    @BeforeEach
    void setUp() {
        accountVerificationService =
                new AccountVerificationServiceImpl(
                        accountRepository,
                        accountVerificationRepository,
                        accountManagementService,
                        authEventPublisher,
                        redisTemplate);
    }

    @Test
    @DisplayName("Verify email: Case-insensitive matching (k8m2p4 == K8M2P4)")
    void testVerifyEmailCaseInsensitive() {
        final String email = "user@example.com";
        final String uppercaseCode = "K8M2P4";
        final String lowercaseInput = "k8m2p4";

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(UuidUtils.v7());

        final String codeHash = HashingUtils.sha256Hex(uppercaseCode);

        final AccountVerification verification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(codeHash)
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .failedAttempts(0)
                        .status(VerificationStatus.PENDING)
                        .build();
        verification.setId(UuidUtils.v7());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.getId(), VerificationStatus.PENDING))
                .thenReturn(Optional.of(verification));
        when(accountVerificationRepository.save(any(AccountVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        final AccountResponse expectedResponse =
                AccountResponse.builder()
                        .accountId(account.getId())
                        .email(email)
                        .status(AccountStatus.ACTIVE)
                        .build();
        when(accountManagementService.toAccountResponse(any(Account.class)))
                .thenReturn(expectedResponse);

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(lowercaseInput).build();

        final AccountResponse response = accountVerificationService.verifyEmail(request);

        assertNotNull(response);
        assertEquals(AccountStatus.ACTIVE, response.getStatus());

        final ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals(AccountStatus.ACTIVE, accountCaptor.getValue().getStatus());

        final ArgumentCaptor<AccountVerification> verificationCaptor =
                ArgumentCaptor.forClass(AccountVerification.class);
        verify(accountVerificationRepository).save(verificationCaptor.capture());
        assertEquals(VerificationStatus.VERIFIED, verificationCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("Verify email: Expired code (>15 minutes) returns HTTP 400")
    void testVerifyEmailExpiredCode() {
        final String email = "user@example.com";
        final String code = "ABC123";

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(UuidUtils.v7());

        final AccountVerification verification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(HashingUtils.sha256Hex(code))
                        .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                        .failedAttempts(0)
                        .status(VerificationStatus.PENDING)
                        .build();
        verification.setId(UuidUtils.v7());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.getId(), VerificationStatus.PENDING))
                .thenReturn(Optional.of(verification));
        when(accountVerificationRepository.save(any(AccountVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(code).build();

        final BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> accountVerificationService.verifyEmail(request));

        assertEquals(AuthResponseCode.VERIFICATION_CODE_EXPIRED, exception.getCode());
        verify(accountVerificationRepository).save(any(AccountVerification.class));
    }

    @Test
    @DisplayName("Verify email: Wrong code increments failedAttempts")
    void testVerifyEmailWrongCodeIncrementsAttempts() {
        final String email = "user@example.com";
        final String correctCode = "K8M2P4";
        final String wrongCode = "WRONG1";

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(UuidUtils.v7());

        final AccountVerification verification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(HashingUtils.sha256Hex(correctCode))
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .failedAttempts(2)
                        .status(VerificationStatus.PENDING)
                        .build();
        verification.setId(UuidUtils.v7());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.getId(), VerificationStatus.PENDING))
                .thenReturn(Optional.of(verification));
        when(accountVerificationRepository.save(any(AccountVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(wrongCode).build();

        final BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> accountVerificationService.verifyEmail(request));

        assertEquals(AuthResponseCode.INVALID_VERIFICATION_CODE, exception.getCode());

        final ArgumentCaptor<AccountVerification> captor =
                ArgumentCaptor.forClass(AccountVerification.class);
        verify(accountVerificationRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getFailedAttempts());
    }

    @Test
    @DisplayName("Verify email: 5th failed attempt updates status to MAX_ATTEMPTS_EXCEEDED")
    void testVerifyEmailMaxAttemptsExceeded() {
        final String email = "user@example.com";
        final String correctCode = "K8M2P4";
        final String wrongCode = "WRONG2";

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(UuidUtils.v7());

        final AccountVerification verification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(HashingUtils.sha256Hex(correctCode))
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .failedAttempts(4)
                        .status(VerificationStatus.PENDING)
                        .build();
        verification.setId(UuidUtils.v7());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.getId(), VerificationStatus.PENDING))
                .thenReturn(Optional.of(verification));
        when(accountVerificationRepository.save(any(AccountVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(wrongCode).build();

        final BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> accountVerificationService.verifyEmail(request));

        assertEquals(AuthResponseCode.INVALID_VERIFICATION_CODE, exception.getCode());

        final ArgumentCaptor<AccountVerification> captor =
                ArgumentCaptor.forClass(AccountVerification.class);
        verify(accountVerificationRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getFailedAttempts());
        assertEquals(VerificationStatus.MAX_ATTEMPTS_EXCEEDED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Verify email: Account not found throws ResourceNotFoundException")
    void testVerifyEmailAccountNotFound() {
        final String email = "nonexistent@example.com";
        final String code = "ABC123";

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(code).build();

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountVerificationService.verifyEmail(request));
    }

    @Test
    @DisplayName("Verify email: Already ACTIVE account throws ConflictException")
    void testVerifyEmailAlreadyActive() {
        final String email = "active@example.com";
        final String code = "ABC123";

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.ACTIVE)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(UuidUtils.v7());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(code).build();

        final ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> accountVerificationService.verifyEmail(request));

        assertEquals(AuthResponseCode.ACCOUNT_ALREADY_ACTIVE, exception.getCode());
    }

    @Test
    @DisplayName("Verify email: No pending verification throws BadRequestException")
    void testVerifyEmailNoPendingVerification() {
        final String email = "user@example.com";
        final String code = "ABC123";

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(UuidUtils.v7());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.getId(), VerificationStatus.PENDING))
                .thenReturn(Optional.empty());

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(code).build();

        final BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> accountVerificationService.verifyEmail(request));

        assertEquals(AuthResponseCode.VERIFICATION_CODE_EXPIRED, exception.getCode());
    }
}
