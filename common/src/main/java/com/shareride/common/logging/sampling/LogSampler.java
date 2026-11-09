package com.shareride.common.logging.sampling;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Intelligent log sampling strategy to prevent log flooding during high traffic. - ERROR and WARN
 * logs are always retained (100%). - Slow requests and errors are always retained (100%). - Normal
 * INFO and DEBUG logs are sampled according to configurable retention probabilities.
 */
public class LogSampler {

    private double infoSampleRate = 1.0; // 100% by default, configurable (e.g. 0.1 for 10%)
    private double debugSampleRate = 0.01; // 1% by default
    private double warnSampleRate = 1.0; // 100%
    private double errorSampleRate = 1.0; // 100%

    public LogSampler() {}

    public LogSampler(double infoSampleRate, double debugSampleRate) {
        this.infoSampleRate = Math.clamp(infoSampleRate, 0.0, 1.0);
        this.debugSampleRate = Math.clamp(debugSampleRate, 0.0, 1.0);
    }

    /**
     * Determines whether a log record should be sampled.
     *
     * @param level the SLF4J log level (e.g. "ERROR", "WARN", "INFO", "DEBUG")
     * @param isSlowOrError true if the request exceeded slow threshold or resulted in >=400 status
     * @return true if log should be recorded, false if it should be dropped/suppressed
     */
    public boolean shouldSample(String level, boolean isSlowOrError) {
        if (isSlowOrError) {
            return true;
        }
        if (level == null) {
            return true;
        }

        return switch (level.toUpperCase()) {
            case "ERROR" ->
                    errorSampleRate >= 1.0
                            || ThreadLocalRandom.current().nextDouble() < errorSampleRate;
            case "WARN" ->
                    warnSampleRate >= 1.0
                            || ThreadLocalRandom.current().nextDouble() < warnSampleRate;
            case "INFO" ->
                    infoSampleRate >= 1.0
                            || ThreadLocalRandom.current().nextDouble() < infoSampleRate;
            case "DEBUG", "TRACE" ->
                    debugSampleRate >= 1.0
                            || ThreadLocalRandom.current().nextDouble() < debugSampleRate;
            default -> true;
        };
    }

    /** Deterministic sampling based on distributed traceId. */
    public boolean shouldSample(String level, String traceId, boolean isSlowOrError) {
        if (isSlowOrError || traceId == null || traceId.isBlank()) {
            return shouldSample(level, isSlowOrError);
        }

        double rate =
                switch (level.toUpperCase()) {
                    case "ERROR" -> errorSampleRate;
                    case "WARN" -> warnSampleRate;
                    case "INFO" -> infoSampleRate;
                    case "DEBUG", "TRACE" -> debugSampleRate;
                    default -> 1.0;
                };

        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0.0) {
            return false;
        }

        // Compute deterministic hash from traceId
        long hash = Math.abs(traceId.hashCode());
        double ratio = (hash % 10000) / 10000.0;
        return ratio < rate;
    }

    public double getInfoSampleRate() {
        return infoSampleRate;
    }

    public void setInfoSampleRate(double infoSampleRate) {
        this.infoSampleRate = Math.clamp(infoSampleRate, 0.0, 1.0);
    }

    public double getDebugSampleRate() {
        return debugSampleRate;
    }

    public void setDebugSampleRate(double debugSampleRate) {
        this.debugSampleRate = Math.clamp(debugSampleRate, 0.0, 1.0);
    }

    public double getWarnSampleRate() {
        return warnSampleRate;
    }

    public void setWarnSampleRate(double warnSampleRate) {
        this.warnSampleRate = Math.clamp(warnSampleRate, 0.0, 1.0);
    }

    public double getErrorSampleRate() {
        return errorSampleRate;
    }

    public void setErrorSampleRate(double errorSampleRate) {
        this.errorSampleRate = Math.clamp(errorSampleRate, 0.0, 1.0);
    }
}
