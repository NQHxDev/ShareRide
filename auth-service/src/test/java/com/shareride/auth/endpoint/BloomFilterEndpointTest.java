package com.shareride.auth.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shareride.auth.service.EmailBloomFilterService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BloomFilterEndpoint Unit Tests")
class BloomFilterEndpointTest {

    @Mock private EmailBloomFilterService emailBloomFilterService;

    @InjectMocks private BloomFilterEndpoint bloomFilterEndpoint;

    @Test
    @DisplayName("Should return success map when rebuild succeeds")
    void testRebuild_Success() {
        when(emailBloomFilterService.rebuildFromDatabase()).thenReturn(150L);

        Map<String, Object> result = bloomFilterEndpoint.rebuild();

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("Bloom filter rebuilt successfully");
        assertThat(result.get("emailsAdded")).isEqualTo(150L);
        assertThat(result.get("durationMs")).isNotNull();
        assertThat(result.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should return failure map when rebuild throws exception")
    void testRebuild_Failure() {
        when(emailBloomFilterService.rebuildFromDatabase())
                .thenThrow(new RuntimeException("Database error"));

        Map<String, Object> result = bloomFilterEndpoint.rebuild();

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).toString().contains("Database error");
        assertThat(result.get("error")).isEqualTo("RuntimeException");
        assertThat(result.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should return statistics when getStats succeeds")
    void testGetStats_Success() {
        EmailBloomFilterService.BloomFilterInfo info =
                new EmailBloomFilterService.BloomFilterInfo(1000000L, 500000L, 1L, 1200L, 2L);
        when(emailBloomFilterService.getFilterInfo()).thenReturn(info);
        when(emailBloomFilterService.isInitialized()).thenReturn(true);

        Map<String, Object> result = bloomFilterEndpoint.getStats();

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("initialized")).isEqualTo(true);
        assertThat(result.get("capacity")).isEqualTo(1000000L);
        assertThat(result.get("size")).isEqualTo(500000L);
        assertThat(result.get("itemsInserted")).isEqualTo(1200L);
        assertThat(result.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should return failure map when getStats throws exception")
    void testGetStats_Failure() {
        when(emailBloomFilterService.getFilterInfo())
                .thenThrow(new IllegalStateException("Valkey offline"));

        Map<String, Object> result = bloomFilterEndpoint.getStats();

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).toString().contains("Failed to retrieve stats");
        assertThat(result.get("timestamp")).isNotNull();
    }
}
