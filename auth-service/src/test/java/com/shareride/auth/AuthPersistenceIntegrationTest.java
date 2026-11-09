package com.shareride.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.domain.User;
import com.shareride.auth.domain.UserRole;
import com.shareride.auth.domain.UserStatus;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.UserResponse;
import com.shareride.auth.repository.UserRepository;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.outbox.OutboxMessage;
import com.shareride.common.outbox.OutboxMessageRepository;
import com.shareride.common.outbox.OutboxStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AuthPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;

    @Autowired private OutboxMessageRepository outboxMessageRepository;

    @Autowired private AuthService authService;

    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        outboxMessageRepository.deleteAll();
        userRepository.deleteAll();
        UserContextHolder.setContext(UserContext.builder().userId("system-admin").build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        outboxMessageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName(
            "Relational Persistence: Should automatically generate UUID and audit timestamps &"
                    + " user")
    void testEntityAuditingAndIdGeneration() {
        User user =
                User.builder()
                        .email("driver.alice@shareride.com")
                        .passwordHash("hashed-pw-123")
                        .fullName("Alice Driver")
                        .phoneNumber("+84988111222")
                        .role(UserRole.DRIVER)
                        .status(UserStatus.ACTIVE)
                        .build();

        User saved = userRepository.saveAndFlush(user);

        assertNotNull(
                saved.getId(), "UUID primary key should be auto-generated before persistence");
        assertEquals(0L, saved.getVersion(), "Initial optimistic lock version must be 0");
        assertNotNull(saved.getCreatedAt(), "Created date must be populated by JPA Auditing");
        assertNotNull(saved.getUpdatedAt(), "Updated date must be populated by JPA Auditing");
        assertEquals(
                "system-admin",
                saved.getCreatedBy(),
                "Created by should match current UserContext");
        assertFalse(saved.isDeleted(), "Entity should not be marked deleted by default");
    }

    @Test
    @DisplayName("Optimistic Locking: Version must increment on update and prevent lost updates")
    void testOptimisticLocking() {
        User user =
                User.builder()
                        .email("rider.bob@shareride.com")
                        .passwordHash("hashed-pw-456")
                        .fullName("Bob Rider")
                        .phoneNumber("+84988333444")
                        .role(UserRole.RIDER)
                        .status(UserStatus.ACTIVE)
                        .build();

        User saved = userRepository.saveAndFlush(user);
        assertEquals(0L, saved.getVersion());

        // Update in new transaction
        transactionTemplate.execute(
                status -> {
                    User toUpdate = userRepository.findById(saved.getId()).orElseThrow();
                    toUpdate.setFullName("Bob Rider Updated");
                    userRepository.saveAndFlush(toUpdate);
                    return null;
                });

        User updated = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Bob Rider Updated", updated.getFullName());
        assertEquals(1L, updated.getVersion(), "Version should have incremented to 1");
    }

    @Test
    @DisplayName("Soft Delete: Soft-deleted entity is preserved in database with audit metadata")
    void testSoftDeleteAudit() {
        User user =
                User.builder()
                        .email("rider.charlie@shareride.com")
                        .passwordHash("hashed-pw-789")
                        .fullName("Charlie Rider")
                        .phoneNumber("+84988555666")
                        .role(UserRole.RIDER)
                        .status(UserStatus.ACTIVE)
                        .build();

        User saved = userRepository.saveAndFlush(user);

        authService.softDeleteUser(saved.getId(), "compliance-officer");

        User fetched = userRepository.findById(saved.getId()).orElseThrow();
        assertTrue(fetched.isDeleted(), "User should be marked as deleted");
        assertNotNull(fetched.getDeletedAt(), "Deleted timestamp should be recorded");
        assertEquals(
                "compliance-officer", fetched.getDeletedBy(), "Deleted by actor must be captured");

        // Verify filtered query does not return soft deleted record
        Optional<User> activeUser =
                userRepository.findByEmailAndDeletedFalse("rider.charlie@shareride.com");
        assertTrue(
                activeUser.isEmpty(), "Soft deleted user should not be returned by active queries");
    }

    @Test
    @DisplayName(
            "Transactional Outbox: Registering user must save User and OutboxMessage atomically in"
                    + " same DB")
    void testTransactionalOutboxAtomicity() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("new.driver@shareride.com")
                        .password("SecurePass123")
                        .fullName("New Driver")
                        .phoneNumber("+84988777888")
                        .role(UserRole.DRIVER)
                        .build();

        UserResponse response = authService.register(request);

        assertNotNull(response.getId());
        assertEquals("new.driver@shareride.com", response.getEmail());

        // Verify User is persisted
        assertTrue(userRepository.existsByEmail("new.driver@shareride.com"));

        // Verify Transactional Outbox record exists in the SAME local database
        List<OutboxMessage> outboxMessages =
                outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertFalse(outboxMessages.isEmpty(), "Outbox event must be persisted transactionally");

        OutboxMessage message =
                outboxMessages.stream()
                        .filter(
                                m ->
                                        AuthOutboxConstants.AGGREGATE_TYPE_USER.equals(
                                                        m.getAggregateType())
                                                && response.getId()
                                                        .toString()
                                                        .equals(m.getAggregateId()))
                        .findFirst()
                        .orElseThrow();

        assertEquals(AuthOutboxConstants.EventType.USER_REGISTERED, message.getEventType());
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertTrue(message.getPayload().contains("new.driver@shareride.com"));
    }

    @Test
    @DisplayName(
            "Authentication: Login should succeed with valid credentials and fail with invalid"
                    + " ones")
    void testLoginWorkflow() {
        RegisterRequest registerRequest =
                RegisterRequest.builder()
                        .email("login.user@shareride.com")
                        .password("CorrectPassword123")
                        .fullName("Login User")
                        .phoneNumber("+84988999000")
                        .role(UserRole.RIDER)
                        .build();
        authService.register(registerRequest);

        // Login success
        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("login.user@shareride.com")
                        .password("CorrectPassword123")
                        .build();
        AuthResponse authResponse = authService.login(loginRequest);

        assertNotNull(authResponse.getAccessToken());
        assertNotNull(authResponse.getRefreshToken());
        assertEquals(AuthConstants.TOKEN_TYPE_BEARER, authResponse.getTokenType());
        assertEquals("login.user@shareride.com", authResponse.getUser().getEmail());

        // Login failure - wrong password
        LoginRequest badLogin =
                LoginRequest.builder()
                        .email("login.user@shareride.com")
                        .password("WrongPassword")
                        .build();
        assertThrows(Exception.class, () -> authService.login(badLogin));
    }

    @Test
    @DisplayName("Validation: Should reject duplicate email registration")
    void testDuplicateEmailRegistration() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("duplicate@shareride.com")
                        .password("Password123")
                        .fullName("Duplicate User")
                        .phoneNumber("+84988111999")
                        .role(UserRole.RIDER)
                        .build();
        authService.register(request);

        RegisterRequest duplicateRequest =
                RegisterRequest.builder()
                        .email("duplicate@shareride.com")
                        .password("Password123")
                        .fullName("Duplicate User 2")
                        .phoneNumber("+84988222999")
                        .role(UserRole.RIDER)
                        .build();

        assertThrows(ConflictException.class, () -> authService.register(duplicateRequest));
    }
}
