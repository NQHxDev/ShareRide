package com.shareride.common.response;

/**
 * Context and configurable defaults for ResponseMetadata. Holds thread-bound API versioning,
 * environment information, and deprecation lifecycle state.
 */
public final class ResponseMetadataContext {

    private static volatile String defaultApiVersion = "v1";
    private static volatile String defaultEnvironment = "development";

    private static final ThreadLocal<String> API_VERSION_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ENVIRONMENT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DEPRECATED_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SUNSET_DATE_HOLDER = new ThreadLocal<>();

    private ResponseMetadataContext() {}

    public static void setDefaultApiVersion(String version) {
        if (version != null && !version.isBlank()) {
            defaultApiVersion = version;
        }
    }

    public static void setDefaultEnvironment(String env) {
        if (env != null && !env.isBlank()) {
            defaultEnvironment = env;
        }
    }

    public static void set(String apiVersion, String environment) {
        setApiVersion(apiVersion);
        setEnvironment(environment);
    }

    public static void setApiVersion(String version) {
        API_VERSION_HOLDER.set(version);
    }

    public static String getApiVersion() {
        String current = API_VERSION_HOLDER.get();
        return current != null ? current : defaultApiVersion;
    }

    public static void setEnvironment(String environment) {
        ENVIRONMENT_HOLDER.set(environment);
    }

    public static String getEnvironment() {
        String current = ENVIRONMENT_HOLDER.get();
        return current != null ? current : defaultEnvironment;
    }

    public static void setDeprecated(boolean deprecated) {
        DEPRECATED_HOLDER.set(deprecated);
    }

    public static boolean isDeprecated() {
        return Boolean.TRUE.equals(DEPRECATED_HOLDER.get());
    }

    public static void setSunsetDate(String sunset) {
        SUNSET_DATE_HOLDER.set(sunset);
    }

    public static String getSunsetDate() {
        return SUNSET_DATE_HOLDER.get();
    }

    public static void clear() {
        API_VERSION_HOLDER.remove();
        ENVIRONMENT_HOLDER.remove();
        DEPRECATED_HOLDER.remove();
        SUNSET_DATE_HOLDER.remove();
    }
}
