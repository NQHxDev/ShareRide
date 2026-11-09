package com.shareride.common.logging;

import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Resolves application, host, and Kubernetes/container infrastructure metadata and populates
 * standard SLF4J MDC fields for structured logging.
 */
public final class EnvironmentContext {

    private static volatile String serviceName = resolveDefaultServiceName();
    private static volatile String serviceVersion = resolveDefaultServiceVersion();
    private static volatile String environment = resolveDefaultEnvironment();
    private static volatile String hostname = resolveDefaultHostname();
    private static volatile String podName = resolveDefaultPodName();
    private static volatile String k8sNamespace = resolveEnv("K8S_NAMESPACE", "NAMESPACE");
    private static volatile String k8sNode = resolveEnv("K8S_NODE", "NODE_NAME");
    private static volatile String k8sContainer = resolveEnv("K8S_CONTAINER", "CONTAINER_NAME");
    private static volatile String k8sCluster = resolveEnv("K8S_CLUSTER", "CLUSTER_NAME");

    private EnvironmentContext() {}

    public static void initialize(String appName, String appVersion, String env) {
        if (appName != null && !appName.isBlank()) {
            serviceName = appName;
        }
        if (appVersion != null && !appVersion.isBlank()) {
            serviceVersion = appVersion;
        }
        if (env != null && !env.isBlank()) {
            environment = env;
        }
    }

    public static void setK8sMetadata(
            String pod, String namespace, String node, String container, String cluster) {
        if (pod != null && !pod.isBlank()) {
            podName = pod;
        }
        if (namespace != null && !namespace.isBlank()) {
            k8sNamespace = namespace;
        }
        if (node != null && !node.isBlank()) {
            k8sNode = node;
        }
        if (container != null && !container.isBlank()) {
            k8sContainer = container;
        }
        if (cluster != null && !cluster.isBlank()) {
            k8sCluster = cluster;
        }
    }

    public static String getServiceName() {
        return serviceName;
    }

    public static void setServiceName(String name) {
        if (name != null && !name.isBlank()) {
            serviceName = name;
        }
    }

    public static String getServiceVersion() {
        return serviceVersion;
    }

    public static void setServiceVersion(String version) {
        if (version != null && !version.isBlank()) {
            serviceVersion = version;
        }
    }

    public static String getEnvironment() {
        return environment;
    }

    public static void setEnvironment(String env) {
        if (env != null && !env.isBlank()) {
            environment = env;
        }
    }

    public static String getHostname() {
        return hostname;
    }

    public static String getPodName() {
        return podName;
    }

    public static String getK8sNamespace() {
        return k8sNamespace;
    }

    public static String getK8sNode() {
        return k8sNode;
    }

    public static String getK8sContainer() {
        return k8sContainer;
    }

    public static String getK8sCluster() {
        return k8sCluster;
    }

    /** Returns an unmodifiable map of all resolved environment and K8s metadata. */
    public static Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(MdcConstants.SERVICE_NAME, serviceName);
        map.put(MdcConstants.SERVICE_VERSION, serviceVersion);
        map.put(MdcConstants.ENVIRONMENT, environment);
        map.put(MdcConstants.HOSTNAME, hostname);
        map.put(MdcConstants.POD_NAME, podName);
        if (podName != null) {
            map.put(MdcConstants.K8S_POD, podName);
        }
        if (k8sNamespace != null) {
            map.put(MdcConstants.K8S_NAMESPACE, k8sNamespace);
        }
        if (k8sNode != null) {
            map.put(MdcConstants.K8S_NODE, k8sNode);
        }
        if (k8sContainer != null) {
            map.put(MdcConstants.K8S_CONTAINER, k8sContainer);
        }
        if (k8sCluster != null) {
            map.put(MdcConstants.K8S_CLUSTER, k8sCluster);
        }
        return Collections.unmodifiableMap(map);
    }

    /** Populates all environment and infrastructure metadata into the current thread's MDC. */
    public static void populateMdc() {
        MDC.put(MdcConstants.SERVICE_NAME, serviceName);
        MDC.put(MdcConstants.SERVICE_VERSION, serviceVersion);
        MDC.put(MdcConstants.ENVIRONMENT, environment);
        MDC.put(MdcConstants.HOSTNAME, hostname);
        MDC.put(MdcConstants.POD_NAME, podName);
        if (podName != null) {
            MDC.put(MdcConstants.K8S_POD, podName);
        }
        if (k8sNamespace != null) {
            MDC.put(MdcConstants.K8S_NAMESPACE, k8sNamespace);
        }
        if (k8sNode != null) {
            MDC.put(MdcConstants.K8S_NODE, k8sNode);
        }
        if (k8sContainer != null) {
            MDC.put(MdcConstants.K8S_CONTAINER, k8sContainer);
        }
        if (k8sCluster != null) {
            MDC.put(MdcConstants.K8S_CLUSTER, k8sCluster);
        }
    }

    private static String resolveDefaultServiceName() {
        String name = System.getProperty("spring.application.name");
        if (name == null || name.isBlank()) {
            name = System.getenv("SPRING_APPLICATION_NAME");
        }
        return (name != null && !name.isBlank()) ? name : "shareride-service";
    }

    private static String resolveDefaultServiceVersion() {
        String ver = System.getProperty("app.version");
        if (ver == null || ver.isBlank()) {
            ver = System.getenv("APP_VERSION");
        }
        return (ver != null && !ver.isBlank()) ? ver : "1.0.0";
    }

    private static String resolveDefaultEnvironment() {
        String env = System.getProperty("spring.profiles.active");
        if (env == null || env.isBlank()) {
            env = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if (env == null || env.isBlank()) {
            env = System.getenv("ENVIRONMENT");
        }
        if (env == null || env.isBlank()) {
            env = System.getenv("ENV");
        }
        return (env != null && !env.isBlank()) ? env : "development";
    }

    private static String resolveDefaultHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            String host = System.getenv("HOSTNAME");
            return (host != null && !host.isBlank()) ? host : "localhost";
        }
    }

    private static String resolveDefaultPodName() {
        String pod = System.getenv("POD_NAME");
        if (pod == null || pod.isBlank()) {
            pod = System.getenv("HOSTNAME");
        }
        return (pod != null && !pod.isBlank()) ? pod : resolveDefaultHostname();
    }

    private static String resolveEnv(String... keys) {
        for (String k : keys) {
            String val = System.getenv(k);
            if (val != null && !val.isBlank()) {
                return val;
            }
            val = System.getProperty(k);
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        return null;
    }
}
