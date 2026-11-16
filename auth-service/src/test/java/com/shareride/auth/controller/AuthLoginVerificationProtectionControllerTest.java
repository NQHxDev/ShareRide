package com.shareride.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.exception.AccountPendingVerificationException;
import com.shareride.auth.service.AuthService;
import com.shareride.common.CommonAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebMvc controller test asserting POST /auth/v1/login returns HTTP 403 Forbidden with payload
 * containing ACCOUNT_PENDING_VERIFICATION code, email, and remainingCooldownSeconds metadata for
 * unverified accounts per FR-011.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CommonAutoConfiguration.class)
@DisplayName("Controller Test: Unverified Account Login Protection")
class AuthLoginVerificationProtectionControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private com.shareride.auth.service.RegistrationService registrationService;

    @MockitoBean
    private com.shareride.auth.service.AccountVerificationService accountVerificationService;

    @Test
    @DisplayName(
            "POST /auth/v1/login should return HTTP 403 Forbidden with"
                    + " ACCOUNT_PENDING_VERIFICATION code and cooldown metadata")
    void shouldReturn403WithCooldownMetadata_whenUnverifiedAccountLogsIn() throws Exception {
        String testEmail = "unverified@example.com";
        int remainingCooldown = 45;

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenThrow(new AccountPendingVerificationException(testEmail, remainingCooldown));

        LoginRequest request = new LoginRequest();
        request.setEmail(testEmail);
        request.setPassword("ValidPassword123!");

        mockMvc.perform(
                        post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("User-Agent", "Test-Agent/1.0")
                                .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_PENDING_VERIFICATION))
                .andExpect(jsonPath("$.message").value(AuthMessage.ACCOUNT_PENDING_VERIFICATION))
                .andExpect(jsonPath("$.data.email").value(testEmail))
                .andExpect(jsonPath("$.data.remainingCooldownSeconds").value(remainingCooldown));
    }

    @Test
    @DisplayName(
            "POST /auth/v1/login should return HTTP 403 with zero cooldown when no active cooldown"
                    + " exists")
    void shouldReturn403WithZeroCooldown_whenNoCooldownActive() throws Exception {
        String testEmail = "unverified-no-cooldown@example.com";

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenThrow(new AccountPendingVerificationException(testEmail, 0));

        LoginRequest request = new LoginRequest();
        request.setEmail(testEmail);
        request.setPassword("ValidPassword123!");

        mockMvc.perform(
                        post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("User-Agent", "Test-Agent/1.0")
                                .header("X-Forwarded-For", "192.168.1.101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_PENDING_VERIFICATION))
                .andExpect(jsonPath("$.data.email").value(testEmail))
                .andExpect(jsonPath("$.data.remainingCooldownSeconds").value(0));
    }
}
