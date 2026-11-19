package com.shareride.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.util.JsonFormat;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.JwkKeyProto;
import com.shareride.gateway.client.AuthServiceGrpcClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayJwksCacheService Unit Tests")
class GatewayJwksCacheServiceTest {

    private static final String JWKS_CACHE_KEY = "gateway:cache:jwks";

    @Mock private ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;
    @Mock private AuthServiceGrpcClient authServiceGrpcClient;

    private GatewayJwksCacheService jwksCacheService;

    @BeforeEach
    void setUp() {
        jwksCacheService =
                new GatewayJwksCacheService(reactiveStringRedisTemplate, authServiceGrpcClient);
    }

    @Test
    @DisplayName("Should return parsed response on JWKS cache hit")
    void testGetJwksWithCache_CacheHit() throws Exception {
        GetJwksResponse expectedResponse =
                GetJwksResponse.newBuilder()
                        .addKeys(
                                JwkKeyProto.newBuilder()
                                        .setKid("test-key-id")
                                        .setKty("OKP")
                                        .setAlg("EdDSA")
                                        .build())
                        .build();
        String json = JsonFormat.printer().print(expectedResponse);

        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(JWKS_CACHE_KEY)).thenReturn(Mono.just(json));

        StepVerifier.create(jwksCacheService.getJwksWithCache())
                .assertNext(
                        response -> {
                            assertThat(response.getKeysCount()).isEqualTo(1);
                            assertThat(response.getKeys(0).getKid()).isEqualTo("test-key-id");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback to fetchAndCacheJwks when cache contains corrupt JSON")
    void testGetJwksWithCache_CorruptCache() {
        GetJwksResponse freshResponse =
                GetJwksResponse.newBuilder()
                        .addKeys(JwkKeyProto.newBuilder().setKid("fresh-key").build())
                        .build();

        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(JWKS_CACHE_KEY)).thenReturn(Mono.just("INVALID_JSON{{{"));
        when(authServiceGrpcClient.getJwks(any(GetJwksRequest.class)))
                .thenReturn(Mono.just(freshResponse));
        when(valueOperations.set(eq(JWKS_CACHE_KEY), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(jwksCacheService.getJwksWithCache())
                .assertNext(
                        response -> {
                            assertThat(response.getKeys(0).getKid()).isEqualTo("fresh-key");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch from authService on cache miss")
    void testGetJwksWithCache_CacheMiss() {
        GetJwksResponse freshResponse =
                GetJwksResponse.newBuilder()
                        .addKeys(JwkKeyProto.newBuilder().setKid("fresh-key-2").build())
                        .build();

        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(JWKS_CACHE_KEY)).thenReturn(Mono.empty());
        when(authServiceGrpcClient.getJwks(any(GetJwksRequest.class)))
                .thenReturn(Mono.just(freshResponse));
        when(valueOperations.set(eq(JWKS_CACHE_KEY), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(jwksCacheService.getJwksWithCache())
                .assertNext(
                        response -> {
                            assertThat(response.getKeys(0).getKid()).isEqualTo("fresh-key-2");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return response even when Redis set fails")
    void testGetJwksWithCache_RedisSetFails() {
        GetJwksResponse freshResponse =
                GetJwksResponse.newBuilder()
                        .addKeys(JwkKeyProto.newBuilder().setKid("fresh-key-3").build())
                        .build();

        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(JWKS_CACHE_KEY)).thenReturn(Mono.empty());
        when(authServiceGrpcClient.getJwks(any(GetJwksRequest.class)))
                .thenReturn(Mono.just(freshResponse));
        when(valueOperations.set(eq(JWKS_CACHE_KEY), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis connection lost")));

        StepVerifier.create(jwksCacheService.getJwksWithCache())
                .assertNext(
                        response -> {
                            assertThat(response.getKeys(0).getKid()).isEqualTo("fresh-key-3");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should delete cache key on invalidateCache")
    void testInvalidateCache() {
        when(reactiveStringRedisTemplate.delete(ArgumentMatchers.<Publisher<String>>any()))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(jwksCacheService.invalidateCache()).verifyComplete();

        verify(reactiveStringRedisTemplate).delete(ArgumentMatchers.<Publisher<String>>any());
    }

    @Test
    @DisplayName("Should return true when kid is found in keys list")
    void testValidateKid_Found() throws Exception {
        GetJwksResponse response =
                GetJwksResponse.newBuilder()
                        .addKeys(JwkKeyProto.newBuilder().setKid("known-kid").build())
                        .build();
        String json = JsonFormat.printer().print(response);

        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(JWKS_CACHE_KEY)).thenReturn(Mono.just(json));

        StepVerifier.create(jwksCacheService.validateKid("known-kid"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should invalidate cache and return false when kid is not found")
    void testValidateKid_NotFound() throws Exception {
        GetJwksResponse response =
                GetJwksResponse.newBuilder()
                        .addKeys(JwkKeyProto.newBuilder().setKid("different-kid").build())
                        .build();
        String json = JsonFormat.printer().print(response);

        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(JWKS_CACHE_KEY)).thenReturn(Mono.just(json));
        when(reactiveStringRedisTemplate.delete(ArgumentMatchers.<Publisher<String>>any()))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(jwksCacheService.validateKid("unknown-kid"))
                .expectNext(false)
                .verifyComplete();

        verify(reactiveStringRedisTemplate).delete(ArgumentMatchers.<Publisher<String>>any());
    }
}
