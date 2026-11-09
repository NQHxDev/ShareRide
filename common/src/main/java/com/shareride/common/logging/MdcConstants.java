package com.shareride.common.logging;

/** Standard MDC keys across all ShareRide microservices. */
public final class MdcConstants {

    // Distributed Tracing & Correlation
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String REQUEST_ID = "requestId";
    public static final String SESSION_ID = "sessionId";
    public static final String ERROR_ID = "errorId";

    // Security & User Context
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String CLIENT_IP = "clientIp";
    public static final String PRIORITY = "priority";
    public static final String FEATURE_FLAGS = "featureFlags";

    // Application & Environment Context
    public static final String SERVICE_NAME = "serviceName";
    public static final String SERVICE_VERSION = "serviceVersion";
    public static final String ENVIRONMENT = "environment";
    public static final String HOSTNAME = "hostname";
    public static final String POD_NAME = "podName";

    // Kubernetes Metadata
    public static final String K8S_POD = "k8s.pod";
    public static final String K8S_NAMESPACE = "k8s.namespace";
    public static final String K8S_NODE = "k8s.node";
    public static final String K8S_CONTAINER = "k8s.container";
    public static final String K8S_CLUSTER = "k8s.cluster";

    // HTTP Request Details
    public static final String HTTP_METHOD = "httpMethod";
    public static final String HTTP_URI = "httpUri";
    public static final String API_VERSION = "apiVersion";

    // Dynamic Debug & Logging Controls
    public static final String LOG_LEVEL = "logLevel";
    public static final String DEBUG_MODE = "debugMode";

    private MdcConstants() {}
}
