package com.shareride.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.controller.AuthController;
import com.shareride.auth.domain.UserRole;
import com.shareride.auth.domain.UserStatus;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.UserResponse;
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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CommonAutoConfiguration.class)
class AuthControllerTest {

   @Autowired
   private MockMvc mockMvc;

   private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

   @MockitoBean
   private AuthService authService;

   @Test
   @DisplayName("POST /api/v1/auth/register should return HTTP 201 Created with BaseResponse")
   void testRegisterSuccess() throws Exception {
      UUID userId = UUID.randomUUID();
      UserResponse userResponse = UserResponse.builder()
            .id(userId)
            .email("test.rider@shareride.com")
            .fullName("Test Rider")
            .phoneNumber("+84900123456")
            .role(UserRole.RIDER)
            .status(UserStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

      when(authService.register(any(RegisterRequest.class))).thenReturn(userResponse);

      RegisterRequest request = RegisterRequest.builder()
            .email("test.rider@shareride.com")
            .password("SecurePass123")
            .fullName("Test Rider")
            .phoneNumber("+84900123456")
            .role(UserRole.RIDER)
            .build();

      mockMvc.perform(post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.REGISTER)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value(AuthResponseCode.USER_REGISTERED))
            .andExpect(jsonPath("$.data.email").value("test.rider@shareride.com"));
   }

   @Test
   @DisplayName("POST /api/v1/auth/login should return HTTP 200 with tokens")
   void testLoginSuccess() throws Exception {
      UUID userId = UUID.randomUUID();
      AuthResponse authResponse = AuthResponse.builder()
            .accessToken("access-token-jwt")
            .refreshToken("refresh-token-uuid")
            .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
            .user(UserResponse.builder().id(userId).email("rider@shareride.com").build())
            .build();

      when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

      LoginRequest request = LoginRequest.builder()
            .email("rider@shareride.com")
            .password("Secret123")
            .build();

      mockMvc.perform(post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.LOGIN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value(AuthResponseCode.LOGIN_SUCCESS))
            .andExpect(jsonPath("$.data.accessToken").value("access-token-jwt"));
   }

   @Test
   @DisplayName("POST /api/v1/auth/logout should revoke tokens and return HTTP 200")
   void testLogoutSuccess() throws Exception {
      doNothing().when(authService).logout(any(), any());

      mockMvc.perform(post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.LOGOUT)
                  .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.BEARER_PREFIX + "token-to-revoke")
                  .param(AuthConstants.PARAM_REFRESH_TOKEN, "rt-to-revoke"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
   }

   @Test
   @DisplayName("GET /api/v1/auth/users/{id} should return user details")
   void testGetUserSuccess() throws Exception {
      UUID userId = UUID.randomUUID();
      UserResponse userResponse = UserResponse.builder()
            .id(userId)
            .email("found@shareride.com")
            .fullName("Found User")
            .phoneNumber("+84999888777")
            .role(UserRole.RIDER)
            .status(UserStatus.ACTIVE)
            .build();

      when(authService.getUserById(userId)).thenReturn(userResponse);

      mockMvc.perform(get(AuthConstants.Endpoint.BASE_PATH + "/users/" + userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.email").value("found@shareride.com"));
   }

}
