package com.shareride.common.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.response.ResponseMetadataContext;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

class ApiVersionInterceptorTest {

    private ShareRideVersioningProperties properties;
    private ApiVersionInterceptor interceptor;

    @RestController
    @ApiVersion("v1")
    static class SampleV1Controller {
        @GetMapping("/items")
        public String getItems() {
            return "v1 items";
        }

        @GetMapping("/items/deprecated")
        @DeprecatedApiVersion(
                sunset = "2026-12-31",
                successor = "/api/v2/items",
                message = "Please upgrade to v2")
        public String getDeprecatedItems() {
            return "old items";
        }
    }

    @RestController
    static class SampleV2Controller {
        @GetMapping("/rides")
        @ApiVersion(
                value = "v2",
                deprecated = true,
                sunset = "2027-01-01",
                successor = "/api/v3/rides")
        public String getRidesV2() {
            return "v2 rides";
        }

        @GetMapping("/rides/multi")
        @ApiVersion({"v1", "v2"})
        public String getRidesMulti() {
            return "multi version rides";
        }
    }

    @BeforeEach
    void setUp() {
        properties = new ShareRideVersioningProperties();
        properties.setSupportedVersions(Set.of("v1", "v2"));
        properties.setEnforcementMode(EnforcementMode.STRICT);
        interceptor = new ApiVersionInterceptor(properties);
        ResponseMetadataContext.clear();
    }

    @AfterEach
    void tearDown() {
        ResponseMetadataContext.clear();
    }

    @Test
    @DisplayName("Endpoint matching declared version should pass through preHandle")
    void testMatchingVersionPasses() throws Exception {
        ResponseMetadataContext.setApiVersion("v1");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/items");
        MockHttpServletResponse res = new MockHttpServletResponse();

        SampleV1Controller controller = new SampleV1Controller();
        Method method = controller.getClass().getMethod("getItems");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        boolean result = interceptor.preHandle(req, res, handlerMethod);
        assertTrue(result);
    }

    @Test
    @DisplayName(
            "Endpoint version mismatch in STRICT mode should throw UnsupportedApiVersionException")
    void testVersionMismatchThrowsException() throws Exception {
        ResponseMetadataContext.setApiVersion("v2");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/items");
        MockHttpServletResponse res = new MockHttpServletResponse();

        SampleV1Controller controller = new SampleV1Controller();
        Method method = controller.getClass().getMethod("getItems");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        UnsupportedApiVersionException ex =
                assertThrows(
                        UnsupportedApiVersionException.class,
                        () -> interceptor.preHandle(req, res, handlerMethod));

        assertEquals("v2", ex.getRequestedVersion());
        assertTrue(ex.getMessage().contains("does not support API version 'v2'"));
    }

    @Test
    @DisplayName("Endpoint version mismatch in PERMISSIVE mode should warn and continue")
    void testVersionMismatchPermissive() throws Exception {
        properties.setEnforcementMode(EnforcementMode.PERMISSIVE);
        ResponseMetadataContext.setApiVersion("v2");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/items");
        MockHttpServletResponse res = new MockHttpServletResponse();

        SampleV1Controller controller = new SampleV1Controller();
        Method method = controller.getClass().getMethod("getItems");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        boolean result = interceptor.preHandle(req, res, handlerMethod);
        assertTrue(result);
        assertNotNull(res.getHeader("X-API-Warn"));
    }

    @Test
    @DisplayName(
            "Endpoint with @DeprecatedApiVersion should attach Deprecation, Sunset, Link, Warning"
                    + " headers")
    void testDeprecatedApiVersionAnnotationHeaders() throws Exception {
        ResponseMetadataContext.setApiVersion("v1");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/items/deprecated");
        MockHttpServletResponse res = new MockHttpServletResponse();

        SampleV1Controller controller = new SampleV1Controller();
        Method method = controller.getClass().getMethod("getDeprecatedItems");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        boolean result = interceptor.preHandle(req, res, handlerMethod);
        assertTrue(result);
        assertTrue(ResponseMetadataContext.isDeprecated());
        assertEquals("2026-12-31", ResponseMetadataContext.getSunsetDate());
        assertEquals("true", res.getHeader("Deprecation"));
        assertEquals("2026-12-31", res.getHeader("Sunset"));
        assertEquals("</api/v2/items>; rel=\"successor-version\"", res.getHeader("Link"));
        assertEquals("299 - \"Please upgrade to v2\"", res.getHeader("Warning"));
    }

    @Test
    @DisplayName(
            "Endpoint with @ApiVersion(deprecated = true) should attach RFC deprecation headers")
    void testApiVersionDeprecatedField() throws Exception {
        ResponseMetadataContext.setApiVersion("v2");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        MockHttpServletResponse res = new MockHttpServletResponse();

        SampleV2Controller controller = new SampleV2Controller();
        Method method = controller.getClass().getMethod("getRidesV2");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        boolean result = interceptor.preHandle(req, res, handlerMethod);
        assertTrue(result);
        assertTrue(ResponseMetadataContext.isDeprecated());
        assertEquals("2027-01-01", ResponseMetadataContext.getSunsetDate());
        assertEquals("true", res.getHeader("Deprecation"));
        assertEquals("2027-01-01", res.getHeader("Sunset"));
        assertEquals("</api/v3/rides>; rel=\"successor-version\"", res.getHeader("Link"));
    }

    @Test
    @DisplayName("Endpoint with multiple supported versions should allow both v1 and v2")
    void testMultiVersionEndpoint() throws Exception {
        SampleV2Controller controller = new SampleV2Controller();
        Method method = controller.getClass().getMethod("getRidesMulti");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        // Call with v1
        ResponseMetadataContext.setApiVersion("v1");
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/rides/multi");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req1, res1, handlerMethod));

        // Call with v2
        ResponseMetadataContext.setApiVersion("v2");
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/rides/multi");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req2, res2, handlerMethod));

        // Call with v3 -> fails
        ResponseMetadataContext.setApiVersion("v3");
        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/rides/multi");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        assertThrows(
                UnsupportedApiVersionException.class,
                () -> interceptor.preHandle(req3, res3, handlerMethod));
    }
}
