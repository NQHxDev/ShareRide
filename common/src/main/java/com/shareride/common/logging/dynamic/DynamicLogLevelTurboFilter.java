package com.shareride.common.logging.dynamic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * TurboFilter that dynamically enables DEBUG/TRACE logging at runtime for specific requests, users,
 * or endpoints without restarting or modifying global configuration.
 */
public class DynamicLogLevelTurboFilter extends TurboFilter {

    private String packagePrefix = "com.shareride";

    public String getPackagePrefix() {
        return packagePrefix;
    }

    public void setPackagePrefix(String packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    @Override
    public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] params,
            Throwable t) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        // Only intervene for DEBUG or TRACE when debug override is active
        if (level.isGreaterOrEqual(Level.INFO)) {
            return FilterReply.NEUTRAL;
        }

        if (DynamicLogLevelContext.isDebugActive()) {
            if (logger != null
                    && logger.getName() != null
                    && (packagePrefix == null || logger.getName().startsWith(packagePrefix))) {
                return FilterReply.ACCEPT;
            }
        }

        return FilterReply.NEUTRAL;
    }
}
