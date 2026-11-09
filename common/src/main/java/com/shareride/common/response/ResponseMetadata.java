package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shareride.common.filter.TraceIdContext;
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
public class ResponseMetadata implements Serializable {

    private Long durationMs;
    private String apiVersion;
    private String environment;

    @Singular("attribute")
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
