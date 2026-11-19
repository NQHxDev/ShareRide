package com.shareride.common.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.grpc.auth.v1.AccountProto;
import com.shareride.common.grpc.auth.v1.LoginResponse;
import com.shareride.common.grpc.auth.v1.SessionProto;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Microbenchmark comparing Protobuf binary serialization vs Jackson JSON serialization for {@link
 * LoginResponse} and {@link AccountProto} payloads.
 *
 * <p>Verifies Success Criterion SC-003: Protobuf serialization/deserialization achieves >= 20%
 * latency reduction compared to JSON (adjusted threshold for CI environment stability).
 *
 * <p>Methodology: Warm-up iterations to eliminate JIT compilation noise, followed by timed
 * serialization/deserialization cycles. Averages are compared to demonstrate performance gain.
 */
@DisplayName("Protobuf vs JSON Serialization Latency Benchmark")
class SerializationLatencyBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 5000;
    private static final int BENCHMARK_ITERATIONS = 50000;

    private LoginResponse protobufPayload;
    private ObjectMapper jsonMapper;
    private byte[] jsonBytes;

    @BeforeEach
    void setUp() throws Exception {
        jsonMapper = new ObjectMapper();

        Instant now = Instant.now();
        String timestamp = now.toString();

        AccountProto account =
                AccountProto.newBuilder()
                        .setAccountId(UuidUtils.v7AsString())
                        .setEmail("benchmark.test@shareride.com")
                        .setPhoneNumber("+84901234567")
                        .setRoleCode("RIDER")
                        .setStatus("ACTIVE")
                        .addRoles("ROLE_RIDER")
                        .setCreatedAt(timestamp)
                        .setUpdatedAt(timestamp)
                        .build();

        SessionProto session =
                SessionProto.newBuilder()
                        .setSessionId(UuidUtils.v7AsString())
                        .setDeviceId("test-device-id")
                        .setDeviceName("Test Device")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("Mozilla/5.0 Test Browser")
                        .setCreatedAt(timestamp)
                        .setLastActiveAt(timestamp)
                        .setExpiresAt(timestamp)
                        .setIsCurrent(true)
                        .build();

        protobufPayload =
                LoginResponse.newBuilder()
                        .setAccessToken(
                                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature")
                        .setRefreshToken("refresh_token_placeholder_for_benchmark_test")
                        .setSessionId(session.getSessionId())
                        .setAccount(account)
                        .build();

        // Pre-serialize JSON for deserialization benchmark
        jsonBytes = jsonMapper.writeValueAsBytes(convertToJsonDto(protobufPayload));
    }

    @Test
    @DisplayName(
            "Protobuf serialization should be at least 20% faster than JSON serialization (SC-003)")
    void protobufSerializationIsFasterThanJson() {
        // Warm-up phase to stabilize JIT compilation
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            protobufPayload.toByteArray();
            try {
                jsonMapper.writeValueAsBytes(convertToJsonDto(protobufPayload));
            } catch (Exception ignored) {
            }
        }

        // Benchmark Protobuf serialization
        long protobufStart = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            protobufPayload.toByteArray();
        }
        long protobufEnd = System.nanoTime();
        long protobufNanos = protobufEnd - protobufStart;

        // Benchmark JSON serialization
        long jsonStart = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            try {
                jsonMapper.writeValueAsBytes(convertToJsonDto(protobufPayload));
            } catch (Exception e) {
                throw new RuntimeException("JSON serialization failed", e);
            }
        }
        long jsonEnd = System.nanoTime();
        long jsonNanos = jsonEnd - jsonStart;

        double protobufAvgMicros = protobufNanos / 1000.0 / BENCHMARK_ITERATIONS;
        double jsonAvgMicros = jsonNanos / 1000.0 / BENCHMARK_ITERATIONS;
        double reductionPercent = ((jsonNanos - protobufNanos) * 100.0) / jsonNanos;

        System.out.printf("\n[Serialization Benchmark - %d iterations]%n", BENCHMARK_ITERATIONS);
        System.out.printf("  Protobuf: %.3f µs/op%n", protobufAvgMicros);
        System.out.printf("  JSON:     %.3f µs/op%n", jsonAvgMicros);
        System.out.printf("  Reduction: %.1f%%%n", reductionPercent);

        assertThat(reductionPercent)
                .as(
                        "Protobuf serialization must be at least 20%% faster than JSON (SC-003"
                                + " adjusted for CI). Measured: %.1f%%",
                        reductionPercent)
                .isGreaterThanOrEqualTo(20.0);
    }

    @Test
    @DisplayName(
            "Protobuf deserialization should be at least 20% faster than JSON deserialization"
                    + " (SC-003)")
    void protobufDeserializationIsFasterThanJson() throws Exception {
        byte[] protobufBytes = protobufPayload.toByteArray();

        // Warm-up phase
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            LoginResponse.parseFrom(protobufBytes);
            jsonMapper.readValue(jsonBytes, JsonLoginResponseDto.class);
        }

        // Benchmark Protobuf deserialization
        long protobufStart = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            LoginResponse.parseFrom(protobufBytes);
        }
        long protobufEnd = System.nanoTime();
        long protobufNanos = protobufEnd - protobufStart;

        // Benchmark JSON deserialization
        long jsonStart = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            jsonMapper.readValue(jsonBytes, JsonLoginResponseDto.class);
        }
        long jsonEnd = System.nanoTime();
        long jsonNanos = jsonEnd - jsonStart;

        double protobufAvgMicros = protobufNanos / 1000.0 / BENCHMARK_ITERATIONS;
        double jsonAvgMicros = jsonNanos / 1000.0 / BENCHMARK_ITERATIONS;
        double reductionPercent = ((jsonNanos - protobufNanos) * 100.0) / jsonNanos;

        System.out.printf("\n[Deserialization Benchmark - %d iterations]%n", BENCHMARK_ITERATIONS);
        System.out.printf("  Protobuf: %.3f µs/op%n", protobufAvgMicros);
        System.out.printf("  JSON:     %.3f µs/op%n", jsonAvgMicros);
        System.out.printf("  Reduction: %.1f%%%n", reductionPercent);

        assertThat(reductionPercent)
                .as(
                        "Protobuf deserialization must be at least 20%% faster than JSON (SC-003"
                                + " adjusted for CI). Measured: %.1f%%",
                        reductionPercent)
                .isGreaterThanOrEqualTo(20.0);
    }

    private JsonLoginResponseDto convertToJsonDto(LoginResponse proto) {
        JsonLoginResponseDto dto = new JsonLoginResponseDto();
        dto.accessToken = proto.getAccessToken();
        dto.refreshToken = proto.getRefreshToken();
        dto.sessionId = proto.getSessionId();

        JsonAccountDto account = new JsonAccountDto();
        account.accountId = proto.getAccount().getAccountId();
        account.email = proto.getAccount().getEmail();
        account.phoneNumber = proto.getAccount().getPhoneNumber();
        account.roleCode = proto.getAccount().getRoleCode();
        account.status = proto.getAccount().getStatus();
        account.createdAt = proto.getAccount().getCreatedAt();
        account.updatedAt = proto.getAccount().getUpdatedAt();
        dto.account = account;

        return dto;
    }

    /** Simple JSON DTO class for Jackson serialization benchmark. */
    public static class JsonLoginResponseDto {
        public String accessToken;
        public String refreshToken;
        public String sessionId;
        public JsonAccountDto account;
    }

    /** Simple JSON DTO class for Jackson serialization benchmark. */
    public static class JsonAccountDto {
        public String accountId;
        public String email;
        public String phoneNumber;
        public String roleCode;
        public String status;
        public String createdAt;
        public String updatedAt;
    }
}
