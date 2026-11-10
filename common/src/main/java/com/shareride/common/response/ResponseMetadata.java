package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shareride.common.filter.TraceIdContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

/**
 * Extended metadata for BaseResponse (execution duration, API version, environment, custom
 * attributes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Execution and runtime metadata")
public class ResponseMetadata implements Serializable {

    @Schema(description = "Server processing duration in milliseconds", example = "42")
    private Long durationMs;

    @Schema(description = "Target API version", example = "v1")
    private String apiVersion;

    @Schema(description = "Current deployment environment", example = "development")
    private String environment;

    @Singular("attribute")
    @Schema(description = "Custom contextual attributes")
    private Map<String, Object> attributes;

    public static ResponseMetadata of(Long durationMs, String apiVersion, String environment) {
        return ResponseMetadata.builder()
                .durationMs(durationMs)
                .apiVersion(apiVersion)
                .environment(environment)
                .build();
    }

    public static ResponseMetadata withDuration(Long durationMs) {
        return of(durationMs, null, null);
    }

    public static ResponseMetadata auto() {
        return of(
                TraceIdContext.getDurationMs(),
                ResponseMetadataContext.getApiVersion(),
                ResponseMetadataContext.getEnvironment());
    }

    public ResponseMetadata addAttribute(String key, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(key, value);
        return this;
    }
}
