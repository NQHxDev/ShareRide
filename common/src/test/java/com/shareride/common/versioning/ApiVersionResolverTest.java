package com.shareride.common.versioning;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiVersionResolverTest {

    private ShareRideVersioningProperties properties;
    private DefaultApiVersionResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new ShareRideVersioningProperties();
        resolver = new DefaultApiVersionResolver(properties);
    }

    @Test
    @DisplayName("Should resolve version from URI path matching /api/v{version}/...")
    void testResolveFromUriPath() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/rides/123");
        Optional<String> version = resolver.resolveVersion(req);

        assertTrue(version.isPresent());
        assertEquals("v1", version.get());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v2.1/rides");
        Optional<String> version2 = resolver.resolveVersion(req2);
        assertTrue(version2.isPresent());
        assertEquals("v2.1", version2.get());
    }

    @Test
    @DisplayName("Should resolve version from Header (X-API-Version, API-Version, Accept-Version)")
    void testResolveFromHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        req.addHeader("X-API-Version", "2");

        Optional<String> version = resolver.resolveVersion(req);
        assertTrue(version.isPresent());
        assertEquals("v2", version.get());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/rides");
        req2.addHeader("API-Version", "v3");
        Optional<String> version2 = resolver.resolveVersion(req2);
        assertTrue(version2.isPresent());
        assertEquals("v3", version2.get());

        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/rides");
        req3.addHeader("Accept-Version", "1.5");
        Optional<String> version3 = resolver.resolveVersion(req3);
        assertTrue(version3.isPresent());
        assertEquals("v1.5", version3.get());
    }

    @Test
    @DisplayName("Should resolve version from Query parameter (api-version or version)")
    void testResolveFromQueryParam() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        req.setParameter("api-version", "v2");

        Optional<String> version = resolver.resolveVersion(req);
        assertTrue(version.isPresent());
        assertEquals("v2", version.get());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/rides");
        req2.setParameter("version", "1");
        Optional<String> version2 = resolver.resolveVersion(req2);
        assertTrue(version2.isPresent());
        assertEquals("v1", version2.get());
    }

    @Test
    @DisplayName("Should resolve version from Accept Header (vendor media type or version param)")
    void testResolveFromAcceptHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        req.addHeader("Accept", "application/vnd.shareride.v2+json");

        Optional<String> version = resolver.resolveVersion(req);
        assertTrue(version.isPresent());
        assertEquals("v2", version.get());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/rides");
        req2.addHeader("Accept", "application/json;version=1");
        Optional<String> version2 = resolver.resolveVersion(req2);
        assertTrue(version2.isPresent());
        assertEquals("v1", version2.get());
    }

    @Test
    @DisplayName("Precedence: URI Path takes precedence over Header, Query, and Accept")
    void testPrecedenceOrder() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/rides");
        req.addHeader("X-API-Version", "v2");
        req.setParameter("api-version", "v3");
        req.addHeader("Accept", "application/vnd.shareride.v4+json");

        Optional<String> version = resolver.resolveVersion(req);
        assertTrue(version.isPresent());
        assertEquals("v1", version.get());

        // If URI Path has no version, Header takes precedence over Query and Accept
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/rides");
        req2.addHeader("X-API-Version", "v2");
        req2.setParameter("api-version", "v3");
        req2.addHeader("Accept", "application/vnd.shareride.v4+json");

        Optional<String> version2 = resolver.resolveVersion(req2);
        assertTrue(version2.isPresent());
        assertEquals("v2", version2.get());
    }

    @Test
    @DisplayName("Should normalize various version formats consistently")
    void testNormalization() {
        assertEquals("v1", ShareRideVersioningProperties.normalizeVersion("1"));
        assertEquals("v1", ShareRideVersioningProperties.normalizeVersion("v1"));
        assertEquals("v1", ShareRideVersioningProperties.normalizeVersion("V1"));
        assertEquals("v2.5", ShareRideVersioningProperties.normalizeVersion("2.5"));
        assertEquals("v2.5", ShareRideVersioningProperties.normalizeVersion("v2.5"));
        assertEquals("", ShareRideVersioningProperties.normalizeVersion(null));
        assertEquals("", ShareRideVersioningProperties.normalizeVersion("   "));
    }

    @Test
    @DisplayName("Should return empty optional when no version information is provided")
    void testNoVersionProvided() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        Optional<String> version = resolver.resolveVersion(req);
        assertTrue(version.isEmpty());
    }
}
