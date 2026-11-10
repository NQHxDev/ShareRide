package com.shareride.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class EnvironmentContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testInitializeAndSetters() {
        EnvironmentContext.initialize("ride-service", "2.1.0", "staging");
        EnvironmentContext.setK8sMetadata(
                "pod-abc-123", "shareride-staging", "node-42", "ride-app", "cluster-sg");

        assertEquals("ride-service", EnvironmentContext.getServiceName());
        assertEquals("2.1.0", EnvironmentContext.getServiceVersion());
        assertEquals("staging", EnvironmentContext.getEnvironment());
        assertEquals("pod-abc-123", EnvironmentContext.getPodName());
        assertEquals("shareride-staging", EnvironmentContext.getK8sNamespace());
        assertEquals("node-42", EnvironmentContext.getK8sNode());
        assertEquals("ride-app", EnvironmentContext.getK8sContainer());
        assertEquals("cluster-sg", EnvironmentContext.getK8sCluster());
    }

    @Test
    void testAsMap() {
        EnvironmentContext.initialize("payment-service", "1.0.0", "prod");
        EnvironmentContext.setK8sMetadata(
                "payment-pod-1", "prod-ns", "k8s-node-1", "container-1", "prod-cluster");

        Map<String, String> map = EnvironmentContext.asMap();
        assertEquals("payment-service", map.get(MdcConstants.SERVICE_NAME));
        assertEquals("1.0.0", map.get(MdcConstants.SERVICE_VERSION));
        assertEquals("prod", map.get(MdcConstants.ENVIRONMENT));
        assertEquals("payment-pod-1", map.get(MdcConstants.K8S_POD));
        assertEquals("prod-ns", map.get(MdcConstants.K8S_NAMESPACE));
        assertEquals("k8s-node-1", map.get(MdcConstants.K8S_NODE));
        assertEquals("container-1", map.get(MdcConstants.K8S_CONTAINER));
        assertEquals("prod-cluster", map.get(MdcConstants.K8S_CLUSTER));
    }

    @Test
    void testPopulateMdc() {
        EnvironmentContext.initialize("gateway-service", "1.2.3", "prod");
        EnvironmentContext.setK8sMetadata("gateway-pod", "default", "worker-1", "gateway", "main");

        EnvironmentContext.populateMdc();

        assertEquals("gateway-service", MDC.get(MdcConstants.SERVICE_NAME));
        assertEquals("1.2.3", MDC.get(MdcConstants.SERVICE_VERSION));
        assertEquals("prod", MDC.get(MdcConstants.ENVIRONMENT));
        assertEquals("gateway-pod", MDC.get(MdcConstants.POD_NAME));
        assertEquals("gateway-pod", MDC.get(MdcConstants.K8S_POD));
        assertEquals("default", MDC.get(MdcConstants.K8S_NAMESPACE));
        assertEquals("worker-1", MDC.get(MdcConstants.K8S_NODE));
        assertEquals("gateway", MDC.get(MdcConstants.K8S_CONTAINER));
        assertEquals("main", MDC.get(MdcConstants.K8S_CLUSTER));
    }
}
