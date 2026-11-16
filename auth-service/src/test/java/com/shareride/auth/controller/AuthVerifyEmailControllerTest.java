package com.shareride.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.VerifyEmailRequest;
import com.shareride.auth.service.AccountVerificationService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.common.CommonAutoConfiguration;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import java.util.Set;
import java.util.UUID;
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
class AuthVerifyEmailControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    @MockitoBean private RegistrationService registrationService;

    @MockitoBean private AccountVerificationService accountVerificationService;

    @Test
    void verifyEmail_ShouldReturnHttp200OkForValidCode() throws Exception {
        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email("user@example.com").code("K8M2P4").build();

        final AccountResponse response =
                AccountResponse.builder()
                        .accountId(UUID.randomUUID())
                        .email("user@example.com")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        when(accountVerificationService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.SUCCESS))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void verifyEmail_ShouldReturnHttp400BadRequestForIncorrectCode() throws Exception {
        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email("user@example.com").code("WRONG1").build();

        when(accountVerificationService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenThrow(
                        new BadRequestException(
                                AuthResponseCode.INVALID_VERIFICATION_CODE,
                                AuthMessage.INVALID_VERIFICATION_CODE));

        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.INVALID_VERIFICATION_CODE))
                .andExpect(jsonPath("$.message").value(AuthMessage.INVALID_VERIFICATION_CODE));
    }

    @Test
    void verifyEmail_ShouldReturnHttp400BadRequestForExpiredCode() throws Exception {
        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email("user@example.com").code("ABC123").build();

        when(accountVerificationService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenThrow(
                        new BadRequestException(
                                AuthResponseCode.VERIFICATION_CODE_EXPIRED,
                                AuthMessage.VERIFICATION_CODE_EXPIRED));

        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.VERIFICATION_CODE_EXPIRED))
                .andExpect(jsonPath("$.message").value(AuthMessage.VERIFICATION_CODE_EXPIRED));
    }

    @Test
    void verifyEmail_ShouldReturnHttp404NotFoundForNonexistentEmail() throws Exception {
        final VerifyEmailRequest request =
                VerifyEmailRequest.builder()
                        .email("nonexistent@example.com")
                        .code("ABC123")
                        .build();

        when(accountVerificationService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenThrow(
                        new ResourceNotFoundException(
                                AuthResponseCode.ACCOUNT_NOT_FOUND, AuthMessage.ACCOUNT_NOT_FOUND));

        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void verifyEmail_ShouldReturnHttp409ConflictForAlreadyActiveAccount() throws Exception {
        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email("active@example.com").code("ABC123").build();

        when(accountVerificationService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenThrow(
                        new ConflictException(
                                AuthResponseCode.ACCOUNT_ALREADY_ACTIVE,
                                AuthMessage.ACCOUNT_ALREADY_ACTIVE));

        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_ALREADY_ACTIVE))
                .andExpect(jsonPath("$.message").value(AuthMessage.ACCOUNT_ALREADY_ACTIVE));
    }

    @Test
    void verifyEmail_ShouldValidateRequiredFields() throws Exception {
        final VerifyEmailRequest invalidRequest =
                VerifyEmailRequest.builder().email("").code("").build();

        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
