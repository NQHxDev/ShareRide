package com.shareride.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.dto.ResendVerificationRequest;
import com.shareride.auth.dto.ResendVerificationResponse;
import com.shareride.auth.service.AccountVerificationService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.common.CommonAutoConfiguration;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CommonAutoConfiguration.class)
class AuthResendVerificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    @MockitoBean private RegistrationService registrationService;

    @MockitoBean private AccountVerificationService accountVerificationService;

    @Test
    void resendVerification_ShouldReturnHttp200OnSuccess() throws Exception {
        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email("user@example.com").build();

        final ResendVerificationResponse response =
                ResendVerificationResponse.builder()
                        .email("user@example.com")
                        .cooldownSeconds(60)
                        .message(AuthMessage.VERIFICATION_CODE_RESENT)
                        .build();

        when(accountVerificationService.resendVerification(any(ResendVerificationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.SUCCESS))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));
    }

    @Test
    void resendVerification_ShouldReturnHttp429WhenCooldownActive() throws Exception {
        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email("user@example.com").build();

        when(accountVerificationService.resendVerification(any(ResendVerificationRequest.class)))
                .thenThrow(
                        new RateLimitExceededException("Vui lòng chờ 45 giây trước khi gửi lại"));

        mockMvc.perform(
                        post("/api/v1/auth/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void resendVerification_ShouldReturnHttp400WhenHourlyLimitExceeded() throws Exception {
        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email("user@example.com").build();

        when(accountVerificationService.resendVerification(any(ResendVerificationRequest.class)))
                .thenThrow(
                        new BadRequestException(
                                AuthResponseCode.RESEND_HOURLY_LIMIT_EXCEEDED,
                                AuthMessage.RESEND_HOURLY_LIMIT_EXCEEDED));

        mockMvc.perform(
                        post("/api/v1/auth/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.RESEND_HOURLY_LIMIT_EXCEEDED));
    }

    @Test
    void resendVerification_ShouldReturnHttp404ForUnknownEmail() throws Exception {
        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email("unknown@example.com").build();

        when(accountVerificationService.resendVerification(any(ResendVerificationRequest.class)))
                .thenThrow(
                        new ResourceNotFoundException(
                                AuthResponseCode.ACCOUNT_NOT_FOUND, AuthMessage.ACCOUNT_NOT_FOUND));

        mockMvc.perform(
                        post("/api/v1/auth/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resendVerification_ShouldReturnHttp409ForAlreadyVerifiedAccount() throws Exception {
        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email("active@example.com").build();

        when(accountVerificationService.resendVerification(any(ResendVerificationRequest.class)))
                .thenThrow(
                        new ConflictException(
                                AuthResponseCode.ACCOUNT_ALREADY_ACTIVE,
                                AuthMessage.ACCOUNT_ALREADY_ACTIVE));

        mockMvc.perform(
                        post("/api/v1/auth/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_ALREADY_ACTIVE));
    }

    @Test
    void resendVerification_ShouldValidateEmailFormat() throws Exception {
        final ResendVerificationRequest invalidRequest =
                ResendVerificationRequest.builder().email("").build();

        mockMvc.perform(
                        post("/api/v1/auth/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
