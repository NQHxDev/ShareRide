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
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.service.AccountVerificationService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.common.CommonAutoConfiguration;
import com.shareride.common.exception.ConflictException;
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
class AuthRegistrationVerificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    @MockitoBean private RegistrationService registrationService;

    @MockitoBean private AccountVerificationService accountVerificationService;

    @Test
    void register_ShouldReturnHttp201WithPendingVerificationStatus() throws Exception {
        final RegisterRequest request =
                RegisterRequest.builder()
                        .email("newuser@example.com")
                        .password("SecurePassword123!")
                        .phoneNumber("+84123456789")
                        .fullName("Nguyen Van A")
                        .build();

        final AccountResponse response =
                AccountResponse.builder()
                        .accountId(UUID.randomUUID())
                        .email("newuser@example.com")
                        .phoneNumber("+84123456789")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        when(registrationService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_REGISTERED))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));
    }

    @Test
    void register_ShouldReturnHttp409WhenEmailPendingVerification() throws Exception {
        final RegisterRequest request =
                RegisterRequest.builder()
                        .email("pending@example.com")
                        .password("Password123!")
                        .phoneNumber("+84987654321")
                        .build();

        when(registrationService.register(any(RegisterRequest.class)))
                .thenThrow(
                        new ConflictException(
                                AuthResponseCode.EMAIL_PENDING_VERIFICATION,
                                AuthMessage.EMAIL_PENDING_VERIFICATION));

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.EMAIL_PENDING_VERIFICATION))
                .andExpect(jsonPath("$.message").value(AuthMessage.EMAIL_PENDING_VERIFICATION));
    }

    @Test
    void register_ShouldValidateRequiredFields() throws Exception {
        final RegisterRequest invalidRequest =
                RegisterRequest.builder().email("").password("").phoneNumber("").build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
