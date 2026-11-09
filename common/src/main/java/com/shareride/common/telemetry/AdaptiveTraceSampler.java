package com.shareride.common.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Adaptive trace sampler that: 1. Drops high-frequency health probes and metrics endpoints
 * (/actuator, /health, /readyz). 2. Always samples high priority / critical transactions (VIP
 * rides, emergency events). 3. Applies parent-based ratio sampling for all remaining traffic.
 */
public class AdaptiveTraceSampler implements Sampler {

    @Nonnull
    private static final AttributeKey<String> HTTP_TARGET_KEY =
            nonNull(AttributeKey.stringKey("http.target"));

    @Nonnull
    private static final AttributeKey<String> URL_PATH_KEY =
            nonNull(AttributeKey.stringKey("url.path"));

    @Nonnull
    private static final AttributeKey<String> PRIORITY_KEY =
            nonNull(AttributeKey.stringKey("priority"));

    private static final Set<String> EXCLUDED_PREFIXES =
            Set.of("/actuator", "/favicon.ico", "/health", "/livez", "/readyz");

    private final double probability;
    private final Sampler delegate;

    public AdaptiveTraceSampler(double probability) {
        this.probability = Math.max(0.0, Math.min(1.0, probability));
        this.delegate = Sampler.parentBased(nonNull(Sampler.traceIdRatioBased(this.probability)));
    }

    public double getProbability() {
        return probability;
    }

    @Override
    public SamplingResult shouldSample(
            @Nonnull Context parentContext,
            @Nonnull String traceId,
            @Nonnull String name,
            @Nonnull SpanKind spanKind,
            @Nonnull Attributes attributes,
            @Nonnull List<LinkData> parentLinks) {

        // 1. Never sample health checks and metrics endpoints to avoid polluting traces
        if (isExcludedPath(name, attributes)) {
            return SamplingResult.drop();
        }

        // 2. Always sample high priority / critical transactions
        if (isHighPriority(attributes)) {
            return SamplingResult.recordAndSample();
        }

        // 3. Delegate to parent-based ratio sampler
        return delegate.shouldSample(
                parentContext, traceId, name, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return String.format("AdaptiveTraceSampler{probability=%.4f}", probability);
    }

    /** Helper to evaluate sampling decision at the HTTP filter layer. */
    public boolean shouldSample(String uri, boolean highPriority, String traceId) {
        if (uri != null) {
            for (String prefix : EXCLUDED_PREFIXES) {
                if (uri.startsWith(prefix)) {
                    return false;
                }
            }
        }
        if (highPriority) {
            return true;
        }
        if (probability >= 1.0) {
            return true;
        }
        if (probability <= 0.0) {
            return false;
        }
        if (traceId != null && traceId.length() >= 8) {
            // Deterministic hash-based sampling on low 32 bits of traceId
            try {
                long hash = Long.parseUnsignedLong(traceId.substring(traceId.length() - 8), 16);
                double ratio = (hash & 0xFFFFFFFFL) / (double) 0x100000000L;
                return ratio < probability;
            } catch (Exception ignored) {
            }
        }
        return Math.random() < probability;
    }

    private boolean isExcludedPath(String name, Attributes attributes) {
        if (name != null) {
            for (String prefix : EXCLUDED_PREFIXES) {
                if (name.contains(prefix)) {
                    return true;
                }
            }
        }
        if (attributes != null) {
            String target = attributes.get(HTTP_TARGET_KEY);
            if (target != null) {
                for (String prefix : EXCLUDED_PREFIXES) {
                    if (target.startsWith(prefix)) {
                        return true;
                    }
                }
            }
            String path = attributes.get(URL_PATH_KEY);
            if (path != null) {
                for (String prefix : EXCLUDED_PREFIXES) {
                    if (path.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isHighPriority(Attributes attributes) {
        if (attributes != null) {
            String priority = attributes.get(PRIORITY_KEY);
            return "HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority);
        }
        return false;
    }

    @Nonnull
    static <T> T nonNull(T value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return value;
    }
}
