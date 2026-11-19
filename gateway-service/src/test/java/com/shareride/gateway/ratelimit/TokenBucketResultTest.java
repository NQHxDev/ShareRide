package com.shareride.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TokenBucketResult Unit Tests")
class TokenBucketResultTest {

    @Test
    @DisplayName("Should parse valid Redis response correctly when allowed")
    void testFromRedisResponse_Allowed() {
        List<?> rawResponse = Arrays.asList(1L, 99L, 1700000000L, 0L);

        TokenBucketResult result = TokenBucketResult.fromRedisResponse(rawResponse);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(99L);
        assertThat(result.getResetTimestamp()).isEqualTo(1700000000L);
        assertThat(result.getRetryAfterSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should parse valid Redis response correctly when rate limited")
    void testFromRedisResponse_RateLimited() {
        List<?> rawResponse = Arrays.asList(0L, 0L, 1700000060L, 60L);

        TokenBucketResult result = TokenBucketResult.fromRedisResponse(rawResponse);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemainingTokens()).isEqualTo(0L);
        assertThat(result.getResetTimestamp()).isEqualTo(1700000060L);
        assertThat(result.getRetryAfterSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when rawResponse is null")
    void testFromRedisResponse_NullResponse() {
        assertThatThrownBy(() -> TokenBucketResult.fromRedisResponse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 4 elements, got null");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when rawResponse has invalid size")
    void testFromRedisResponse_InvalidSize() {
        List<?> rawResponse = List.of(1L, 2L);

        assertThatThrownBy(() -> TokenBucketResult.fromRedisResponse(rawResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 4 elements, got 2");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when element is not a number")
    void testFromRedisResponse_NonNumericElement() {
        List<?> rawResponse = Arrays.asList("not_a_number", 0L, 0L, 0L);

        assertThatThrownBy(() -> TokenBucketResult.fromRedisResponse(rawResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected numeric value, got: class java.lang.String");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when element is null")
    void testFromRedisResponse_NullElement() {
        List<?> rawResponse = Arrays.asList(null, 0L, 0L, 0L);

        assertThatThrownBy(() -> TokenBucketResult.fromRedisResponse(rawResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected numeric value, got: null");
    }
}
