package com.shareride.auth.service;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthSecurityAuditConstants;
import com.shareride.auth.domain.RefreshToken;
import com.shareride.auth.domain.User;
import com.shareride.auth.domain.UserRole;
import com.shareride.auth.domain.UserStatus;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.UserResponse;
import com.shareride.auth.repository.RefreshTokenRepository;
import com.shareride.auth.repository.UserRepository;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.SecurityAuditEvent;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.redis.DistributedLockService;
import com.shareride.common.redis.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Service xử lý các nghiệp vụ xác thực người dùng, bảo mật và quản trị tài khoản trong Auth Service.
 * <p>
 * Đảm bảo các chuẩn mực:
 * <ul>
 *    <li>Phòng chống Race-Condition khi đăng ký bằng Redis Distributed Lock (Redisson)</li>
 *    <li>Tính nhất quán dữ liệu qua Transactional Outbox Pattern</li>
 *    <li>Thu hồi phiên làm việc qua Redis Token Blacklist</li>
 *    <li>Ghi vết kiểm toán an ninh với Security Audit Logger theo chuẩn SOC/SIEM</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class AuthService {

   private final UserRepository userRepository;
   private final RefreshTokenRepository refreshTokenRepository;
   private final OutboxService outboxService;
   private final DistributedLockService distributedLockService;
   private final TokenBlacklistService tokenBlacklistService;
   private final AuditLogService auditLogService;

   public AuthService(
         UserRepository userRepository,
         RefreshTokenRepository refreshTokenRepository,
         OutboxService outboxService,
         ObjectProvider<DistributedLockService> distributedLockServiceProvider,
         ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
         AuditLogService auditLogService) {
      this.userRepository = userRepository;
      this.refreshTokenRepository = refreshTokenRepository;
      this.outboxService = outboxService;
      this.distributedLockService = distributedLockServiceProvider.getIfAvailable();
      this.tokenBlacklistService = tokenBlacklistServiceProvider.getIfAvailable();
      this.auditLogService = auditLogService;
   }

   /**
    * Đăng ký tài khoản người dùng mới.
    * Sử dụng Redis Distributed Lock để ngăn chặn tranh chấp dữ liệu (race condition)
    * khi có nhiều yêu cầu đăng ký cùng một địa chỉ email tại cùng một thời điểm.
    * Lưu thực thể User và bản tin Transactional Outbox trong cùng một transaction PostgreSQL ACID.
    *
    * @param request thông tin đăng ký người dùng
    * @return thông tin chi tiết người dùng sau khi lưu thành công
    */
   @Transactional
   public UserResponse register(RegisterRequest request) {
      String lockKey = AuthConstants.RedisLock.REGISTER_EMAIL_PREFIX + request.getEmail().toLowerCase().trim();

      if (distributedLockService != null) {
         return distributedLockService.executeWithLock(
               lockKey,
               AuthConstants.RedisLock.REGISTER_LOCK_TIMEOUT,
               () -> doRegister(request)
         );
      }
      return doRegister(request);
   }

   private UserResponse doRegister(RegisterRequest request) {
      String normalizedEmail = request.getEmail().toLowerCase().trim();

      if (userRepository.existsByEmail(normalizedEmail)) {
         throw new ConflictException(AuthResponseCode.EMAIL_ALREADY_EXISTS, AuthMessage.emailAlreadyExists(normalizedEmail));
      }
      if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
         throw new ConflictException(AuthResponseCode.PHONE_ALREADY_EXISTS, AuthMessage.phoneNumberAlreadyExists(request.getPhoneNumber()));
      }

      User user = User.builder()
            .email(normalizedEmail)
            .passwordHash(hashPassword(request.getPassword()))
            .fullName(request.getFullName().trim())
            .phoneNumber(request.getPhoneNumber().trim())
            .role(request.getRole() != null ? request.getRole() : UserRole.RIDER)
            .status(UserStatus.ACTIVE)
            .build();

      User savedUser = userRepository.save(user);

      // Lưu sự kiện Outbox trong cùng một Transaction để đảm bảo tính nhất quán Dual-write
      outboxService.recordEvent(
            AuthOutboxConstants.AGGREGATE_TYPE_USER,
            savedUser.getId().toString(),
            AuthOutboxConstants.EventType.USER_REGISTERED,
            Map.of(
                  AuthOutboxConstants.PayloadKey.USER_ID, savedUser.getId(),
                  AuthOutboxConstants.PayloadKey.EMAIL, savedUser.getEmail(),
                  AuthOutboxConstants.PayloadKey.FULL_NAME, savedUser.getFullName(),
                  AuthOutboxConstants.PayloadKey.ROLE, savedUser.getRole().name()
            )
      );

      auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
            .eventType(AuthSecurityAuditConstants.EventType.USER_REGISTRATION)
            .userId(savedUser.getId().toString())
            .resource(AuthConstants.AuditResource.REGISTER)
            .status(AuthSecurityAuditConstants.Status.SUCCESS)
            .build());

      log.info("Successfully registered user [{}] with role [{}]", savedUser.getId(), savedUser.getRole());
      return toUserResponse(savedUser);
   }

   /**
    * Xác thực thông tin tài khoản người dùng và cấp phát cặp token (Access Token, Refresh Token).
    *
    * @param request thông tin đăng nhập gồm email và password
    * @return cặp Access Token, Refresh Token và thông tin cơ bản của User
    */
   @Transactional
   public AuthResponse login(LoginRequest request) {
      String normalizedEmail = request.getEmail().toLowerCase().trim();
      User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
            .orElseThrow(() -> {
               auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
                     .eventType(AuthSecurityAuditConstants.EventType.LOGIN_FAILURE)
                     .resource(AuthConstants.AuditResource.LOGIN)
                     .status(AuthSecurityAuditConstants.Status.USER_NOT_FOUND)
                     .build());
               return new UnauthorizedException(AuthMessage.INVALID_EMAIL_OR_PASSWORD);
            });

      if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
         auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
               .eventType(AuthSecurityAuditConstants.EventType.LOGIN_FAILURE)
               .userId(user.getId().toString())
               .resource(AuthConstants.AuditResource.LOGIN)
               .status(AuthSecurityAuditConstants.Status.INVALID_CREDENTIALS)
               .build());
         throw new UnauthorizedException(AuthMessage.INVALID_EMAIL_OR_PASSWORD);
      }

      if (user.getStatus() != UserStatus.ACTIVE) {
         throw new UnauthorizedException(AuthMessage.userAccountInactive(user.getStatus()));
      }

      String accessToken = UUID.randomUUID().toString();
      String refreshTokenString = UUID.randomUUID().toString();

      RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(hashPassword(refreshTokenString))
            .expiresAt(Instant.now().plus(AuthConstants.TokenTtl.REFRESH_TOKEN_VALIDITY))
            .revoked(false)
            .build();

      refreshTokenRepository.save(refreshToken);

      auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
            .eventType(AuthSecurityAuditConstants.EventType.LOGIN_SUCCESS)
            .userId(user.getId().toString())
            .resource(AuthConstants.AuditResource.LOGIN)
            .status(AuthSecurityAuditConstants.Status.SUCCESS)
            .build());

      return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshTokenString)
            .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
            .expiresIn(Instant.now().plus(AuthConstants.TokenTtl.ACCESS_TOKEN_VALIDITY))
            .user(toUserResponse(user))
            .build();
   }

   /**
    * Đăng xuất người dùng, vô hiệu hóa Access Token qua Redis Token Blacklist
    * và đánh dấu thu hồi (revoked = true) Refresh Token trong cơ sở dữ liệu.
    *
    * @param accessToken        mã Access Token cần đưa vào blacklist
    * @param refreshTokenString mã Refresh Token cần thu hồi
    */
   @Transactional
   public void logout(String accessToken, String refreshTokenString) {
      if (tokenBlacklistService != null && accessToken != null && !accessToken.isBlank()) {
         tokenBlacklistService.blacklistToken(accessToken, AuthConstants.TokenTtl.ACCESS_TOKEN_VALIDITY);
      }

      if (refreshTokenString != null && !refreshTokenString.isBlank()) {
         String hash = hashPassword(refreshTokenString);
         refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
         });
      }

      UserContext context = UserContextHolder.getContext();
      String userId = (context != null && context.getUserId() != null) ? context.getUserId() : AuthConstants.UNKNOWN_USER_ID;

      auditLogService.logSecurityEvent(SecurityAuditEvent.builder()
            .eventType(AuthSecurityAuditConstants.EventType.LOGOUT)
            .userId(userId)
            .resource(AuthConstants.AuditResource.LOGOUT)
            .status(AuthSecurityAuditConstants.Status.SUCCESS)
            .build());

      log.info("User [{}] logged out, token revoked", userId);
   }

   /**
    * Tra cứu chi tiết hồ sơ người dùng theo UUID định danh.
    *
    * @param userId UUID của người dùng cần tìm
    * @return thông tin UserResponse
    * @throws ResourceNotFoundException nếu không tìm thấy hoặc người dùng đã bị xóa mềm
    */
   @Transactional(readOnly = true)
   public UserResponse getUserById(UUID userId) {
      User user = userRepository.findById(userId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException(AuthOutboxConstants.AGGREGATE_TYPE_USER, userId));
      return toUserResponse(user);
   }

   /**
    * Xóa mềm tài khoản người dùng và xuất bản Domain Event sang Transactional Outbox.
    *
    * @param userId    UUID người dùng cần xóa
    * @param deletedBy định danh người thực hiện xóa mềm (auditor/admin)
    */
   @Transactional
   public void softDeleteUser(UUID userId, String deletedBy) {
      User user = userRepository.findById(userId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException(AuthOutboxConstants.AGGREGATE_TYPE_USER, userId));
      user.markDeleted(deletedBy);
      userRepository.save(user);

      outboxService.recordEvent(
            AuthOutboxConstants.AGGREGATE_TYPE_USER,
            userId.toString(),
            AuthOutboxConstants.EventType.USER_DELETED,
            Map.of(AuthOutboxConstants.PayloadKey.USER_ID, userId)
      );
      log.info("User [{}] soft deleted by [{}]", userId, deletedBy);
   }

   private UserResponse toUserResponse(User user) {
      return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .status(user.getStatus())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
   }

   private String hashPassword(String raw) {
      try {
         MessageDigest digest = MessageDigest.getInstance(AuthConstants.HASH_ALGORITHM_SHA256);
         byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(hash);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(AuthConstants.HASH_ALGORITHM_SHA256 + " algorithm not available", e);
      }
   }

   private boolean verifyPassword(String raw, String hash) {
      return hashPassword(raw).equals(hash);
   }

}
