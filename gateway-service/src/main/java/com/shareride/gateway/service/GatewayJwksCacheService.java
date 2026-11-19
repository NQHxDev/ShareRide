package com.shareride.gateway.service;

import com.google.protobuf.util.JsonFormat;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.gateway.client.AuthServiceGrpcClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * JWKS discovery caching service for Gateway. Implements Valkey-backed caching with 24-hour TTL and
 * reactive invalidation per T025 (User Story 1).
 *
 * <p>Cache strategy:
 *
 * <ul>
 *   <li>Cache JWKS response in Valkey with 24-hour TTL
 *   <li>Reactive invalidation on unknown kid (key rotation detection)
 *   <li>Proactive event-based invalidation on key rotation events
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayJwksCacheService {

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final AuthServiceGrpcClient authServiceGrpcClient;

    private static final String JWKS_CACHE_KEY = "gateway:cache:jwks";
    private static final Duration JWKS_TTL = Duration.ofHours(24);

    /**
     * Get JWKS with caching. Fetches from cache if available, otherwise calls Auth Service and
     * caches.
     *
     * @return Mono emitting GetJwksResponse
     */
    public Mono<GetJwksResponse> getJwksWithCache() {
        return reactiveStringRedisTemplate
                .opsForValue()
                .get(JWKS_CACHE_KEY)
                .flatMap(
                        cachedJson -> {
                            try {
                                GetJwksResponse.Builder builder = GetJwksResponse.newBuilder();
                                JsonFormat.parser().merge(cachedJson, builder);
                                log.debug("JWKS cache hit");
                                return Mono.just(builder.build());
                            } catch (Exception e) {
                                log.warn(
                                        "Failed to parse cached JWKS, fetching fresh: {}",
                                        e.getMessage());
                                return fetchAndCacheJwks();
                            }
                        })
                .switchIfEmpty(
                        Mono.defer(
                                () -> {
                                    log.debug("JWKS cache miss, fetching from Auth Service");
                                    return fetchAndCacheJwks();
                                }));
    }

    /**
     * Invalidate JWKS cache. Used for reactive invalidation on unknown kid or manual cache clear.
     *
     * @return Mono completing when cache is cleared
     */
    public Mono<Void> invalidateCache() {
        log.info("Invalidating JWKS cache");
        return reactiveStringRedisTemplate.delete(Mono.just(JWKS_CACHE_KEY)).then();
    }

    /**
     * Fetch JWKS from Auth Service and cache the result.
     *
     * @return Mono emitting fresh GetJwksResponse
     */
    private Mono<GetJwksResponse> fetchAndCacheJwks() {
        return authServiceGrpcClient
                .getJwks(GetJwksRequest.newBuilder().build())
                .flatMap(
                        response -> {
                            try {
                                String jsonResponse = JsonFormat.printer().print(response);
                                return reactiveStringRedisTemplate
                                        .opsForValue()
                                        .set(JWKS_CACHE_KEY, jsonResponse, JWKS_TTL)
                                        .thenReturn(response)
                                        .doOnSuccess(
                                                r ->
                                                        log.info(
                                                                "JWKS cached successfully with {}"
                                                                        + " keys",
                                                                response.getKeysCount()))
                                        .onErrorResume(
                                                ex -> {
                                                    log.error(
                                                            "Failed to cache JWKS: {}",
                                                            ex.getMessage());
                                                    return Mono.just(response);
                                                });
                            } catch (Exception e) {
                                log.error("Failed to cache JWKS: {}", e.getMessage());
                                return Mono.just(response);
                            }
                        });
    }

    /**
     * Check if a specific kid exists in cached JWKS. If kid is not found, invalidate cache and
     * return false.
     *
     * @param kid key ID to check
     * @return Mono emitting true if kid exists, false otherwise
     */
    public Mono<Boolean> validateKid(String kid) {
        return getJwksWithCache()
                .map(jwks -> jwks.getKeysList().stream().anyMatch(key -> key.getKid().equals(kid)))
                .flatMap(
                        exists -> {
                            if (!exists) {
                                log.warn("Unknown kid detected: {}, invalidating cache", kid);
                                return invalidateCache().thenReturn(false);
                            }
                            return Mono.just(true);
                        });
    }
}
