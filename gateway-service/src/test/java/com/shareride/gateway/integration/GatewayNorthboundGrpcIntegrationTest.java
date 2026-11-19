package com.shareride.gateway.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.JwkKeyProto;
import com.shareride.common.grpc.auth.v1.LoginResponse;
import com.shareride.common.grpc.auth.v1.RegisterResponse;
import com.shareride.gateway.client.AuthServiceGrpcClient;
import com.shareride.gateway.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Integration test validating end-to-end HTTP-to-gRPC translation in Gateway Service. Tests
 * external HTTP/REST requests translating to internal gRPC calls to downstream services. Uses
 * mocked gRPC infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.main.allow-bean-definition-overriding=true"})
class GatewayNorthboundGrpcIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    private WebTestClient webTestClient;

    @Autowired private AuthServiceGrpcClient authServiceGrpcClient;

    @BeforeEach
    void setUp() {
        this.webTestClient =
                WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @TestConfiguration
    static class MockGrpcClientConfig {
        @Bean
        @Primary
        public AuthServiceGrpcClient authServiceGrpcClient() {
            return mock(AuthServiceGrpcClient.class);
        }
    }

    @Test
    void testRegisterEndpoint_shouldTranslateHttpToGrpcSuccessfully() {
        // Mock gRPC response
        RegisterResponse grpcResponse =
                RegisterResponse.newBuilder()
                        .setAccountId("01932d5e-3f85-7e3a-82e1-2e3f4e5f6a7b")
                        .setEmail("test@example.com")
                        .setRoleCode("RIDER")
                        .setVerificationStatus("PENDING")
                        .setCreatedAt("2026-11-17T14:55:20.789Z")
                        .build();
        when(authServiceGrpcClient.register(any())).thenReturn(Mono.just(grpcResponse));

        Map<String, Object> registerRequest =
                Map.of(
                        "email", "test@example.com",
                        "password", "SecurePass123",
                        "confirmPassword", "SecurePass123");

        webTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accountId")
                .exists()
                .jsonPath("$.data.email")
                .isEqualTo("test@example.com")
                .jsonPath("$.data.roleCode")
                .isEqualTo("RIDER")
                .jsonPath("$.data.verificationStatus")
                .isEqualTo("PENDING");
    }

    @Test
    void testLoginEndpoint_shouldTranslateHttpToGrpcSuccessfully() {
        // Mock gRPC response
        com.shareride.common.grpc.auth.v1.AccountProto account =
                com.shareride.common.grpc.auth.v1.AccountProto.newBuilder()
                        .setAccountId("01932d5e-3f85-7e3a-82e1-2e3f4e5f6a7b")
                        .setEmail("test@example.com")
                        .setRoleCode("RIDER")
                        .build();

        LoginResponse grpcResponse =
                LoginResponse.newBuilder()
                        .setAccessToken("mock.access.token")
                        .setRefreshToken("mock.refresh.token")
                        .setTokenType("Bearer")
                        .setExpiresIn(3600)
                        .setSessionId("01932d5f-1234-7e3a-82e1-abcdef123456")
                        .setAccount(account)
                        .build();
        when(authServiceGrpcClient.login(any())).thenReturn(Mono.just(grpcResponse));

        Map<String, Object> loginRequest =
                Map.of("email", "test@example.com", "password", "SecurePass123");

        webTestClient
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accessToken")
                .exists()
                .jsonPath("$.data.refreshToken")
                .exists()
                .jsonPath("$.data.tokenType")
                .isEqualTo("Bearer");
    }

    @Test
    void testGetAccountEndpoint_shouldTranslateHttpToGrpcSuccessfully() {
        String accountId = "01932d5e-3f85-7e3a-82e1-2e3f4e5f6a7b";

        // Mock gRPC response
        com.shareride.common.grpc.auth.v1.AccountProto account =
                com.shareride.common.grpc.auth.v1.AccountProto.newBuilder()
                        .setAccountId(accountId)
                        .setEmail("test@example.com")
                        .setPhoneNumber("+84901234567")
                        .setRoleCode("RIDER")
                        .setStatus("ACTIVE")
                        .addRoles("USER")
                        .addPermissions("READ")
                        .setCreatedAt("2026-11-17T14:55:20.789Z")
                        .setUpdatedAt("2026-11-17T14:55:20.789Z")
                        .build();

        GetAccountResponse grpcResponse =
                GetAccountResponse.newBuilder().setAccount(account).build();
        when(authServiceGrpcClient.getAccount(any())).thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/auth/accounts/{id}", accountId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.account.accountId")
                .isEqualTo(accountId)
                .jsonPath("$.data.account.email")
                .exists();
    }

    @Test
    void testGetJwksEndpoint_shouldTranslateHttpToGrpcSuccessfully() {
        // Mock gRPC response
        JwkKeyProto key =
                JwkKeyProto.newBuilder()
                        .setKty("OKP")
                        .setCrv("Ed25519")
                        .setX("mockBase64EncodedPublicKey")
                        .setKid("key-001")
                        .setUse("sig")
                        .setAlg("EdDSA")
                        .build();

        GetJwksResponse grpcResponse = GetJwksResponse.newBuilder().addKeys(key).build();
        when(authServiceGrpcClient.getJwks(any(GetJwksRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/auth/.well-known/jwks.json")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.keys")
                .isArray()
                .jsonPath("$.keys[0].kty")
                .exists()
                .jsonPath("$.keys[0].kid")
                .exists();
    }

    @Test
    void testInvalidRequest_shouldPassThroughToAuthService() {
        // Note: DTOs currently don't have validation annotations, so invalid data
        // is passed through to auth-service which will validate. Mock accepts anything.
        RegisterResponse grpcResponse =
                RegisterResponse.newBuilder()
                        .setAccountId("01932d5e-3f85-7e3a-82e1-2e3f4e5f6a7b")
                        .setEmail("invalid-email")
                        .setRoleCode("RIDER")
                        .setVerificationStatus("PENDING")
                        .build();
        when(authServiceGrpcClient.register(any())).thenReturn(Mono.just(grpcResponse));

        Map<String, Object> invalidRegisterRequest =
                Map.of(
                        "email", "invalid-email",
                        "password", "short",
                        "confirmPassword", "short");

        webTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRegisterRequest)
                .exchange()
                .expectStatus()
                .isCreated(); // Mock accepts anything
    }
}
