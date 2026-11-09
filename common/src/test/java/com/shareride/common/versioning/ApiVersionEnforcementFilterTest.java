package com.shareride.common.versioning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.response.ResponseMetadataContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiVersionEnforcementFilterTest {

    private ShareRideVersioningProperties properties;
    private DefaultApiVersionResolver resolver;
    private ShareRideMetrics mockMetrics;
    private TemplateMessageSource messageSource;
    private ObjectMapper objectMapper;
    private ApiVersionEnforcementFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ShareRideVersioningProperties();
        properties.setSupportedVersions(Set.of("v1", "v2"));
        properties.setDefaultVersion("v1");
        properties.setEnforcementMode(EnforcementMode.STRICT);

        resolver = new DefaultApiVersionResolver(properties);
        mockMetrics = mock(ShareRideMetrics.class);

        messageSource = new TemplateMessageSource();
        messageSource.init();

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("messageSource", messageSource);
        beanFactory.addBean("metrics", mockMetrics);
        beanFactory.addBean("objectMapper", objectMapper);

        filter =
                new ApiVersionEnforcementFilter(
                        properties,
                        resolver,
                        beanFactory.getBeanProvider(TemplateMessageSource.class),
                        beanFactory.getBeanProvider(ShareRideMetrics.class),
                        beanFactory.getBeanProvider(ObjectMapper.class));

        ResponseMetadataContext.clear();
    }

    @AfterEach
    void tearDown() {
        ResponseMetadataContext.clear();
    }

    @Test
    @DisplayName(
            "STRICT mode: Valid version passes through, populates context, and adds response"
                    + " header")
    void testStrictModeWithValidVersion() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/rides");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain =
                (r, s) -> {
                    chainCalled.set(true);
                    assertEquals("v2", ResponseMetadataContext.getApiVersion());
                    assertFalse(ResponseMetadataContext.isDeprecated());
                };

        filter.doFilter(req, res, chain);

        assertTrue(chainCalled.get());
        assertEquals("v2", res.getHeader("X-API-Version"));
        verify(mockMetrics).recordApiVersionRequest("v2", "SUPPORTED");
    }

    @Test
    @DisplayName(
            "STRICT mode: Unsupported version is rejected with HTTP 400 and BaseResponse error")
    void testStrictModeWithUnsupportedVersion() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        req.addHeader("X-API-Version", "v99");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, res, chain);

        assertFalse(chainCalled.get(), "FilterChain should not be called when version is rejected");
        assertEquals(400, res.getStatus());
        assertTrue(res.getContentType().contains("application/json"));

        String json = res.getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        assertFalse(node.get("success").asBoolean());
        assertEquals("UNSUPPORTED_API_VERSION", node.get("code").asText());
        assertNotNull(node.get("message").asText());
        assertNotNull(node.get("timestamp"));
        verify(mockMetrics).recordApiVersionRequest("v99", "UNSUPPORTED");
    }

    @Test
    @DisplayName(
            "STRICT mode: Missing version falls back to default version when requireVersion=false")
    void testStrictModeMissingVersionFallback() throws ServletException, IOException {
        properties.setRequireVersion(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain =
                (r, s) -> {
                    chainCalled.set(true);
                    assertEquals("v1", ResponseMetadataContext.getApiVersion());
                };

        filter.doFilter(req, res, chain);

        assertTrue(chainCalled.get());
        assertEquals("v1", res.getHeader("X-API-Version"));
    }

    @Test
    @DisplayName("STRICT mode: Missing version is rejected with HTTP 400 when requireVersion=true")
    void testStrictModeMissingVersionRequired() throws ServletException, IOException {
        properties.setRequireVersion(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, res, chain);

        assertFalse(chainCalled.get());
        assertEquals(400, res.getStatus());
        JsonNode node = objectMapper.readTree(res.getContentAsString());
        assertEquals("UNSUPPORTED_API_VERSION", node.get("code").asText());
        verify(mockMetrics).recordApiVersionRequest("missing", "REQUIRED_MISSING");
    }

    @Test
    @DisplayName(
            "PERMISSIVE mode: Unsupported version falls back to default with X-API-Warn header")
    void testPermissiveModeFallback() throws ServletException, IOException {
        properties.setEnforcementMode(EnforcementMode.PERMISSIVE);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        req.addHeader("X-API-Version", "v5");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain =
                (r, s) -> {
                    chainCalled.set(true);
                    assertEquals("v1", ResponseMetadataContext.getApiVersion());
                };

        filter.doFilter(req, res, chain);

        assertTrue(chainCalled.get());
        assertEquals("v1", res.getHeader("X-API-Version"));
        assertNotNull(res.getHeader("X-API-Warn"));
        assertTrue(res.getHeader("X-API-Warn").contains("v5"));
        verify(mockMetrics).recordApiVersionRequest("v5", "PERMISSIVE_FALLBACK");
    }

    @Test
    @DisplayName(
            "Deprecated version: Attaches Deprecation, Sunset, and Warning headers and records"
                    + " metric")
    void testDeprecatedVersionHeaders() throws ServletException, IOException {
        properties.setDeprecatedVersions(Set.of("v1"));
        properties.setSunsetDates(Map.of("v1", "2026-12-31T23:59:59Z"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/rides");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain =
                (r, s) -> {
                    chainCalled.set(true);
                    assertTrue(ResponseMetadataContext.isDeprecated());
                    assertEquals("2026-12-31T23:59:59Z", ResponseMetadataContext.getSunsetDate());
                };

        filter.doFilter(req, res, chain);

        assertTrue(chainCalled.get());
        assertEquals("true", res.getHeader("Deprecation"));
        assertEquals("2026-12-31T23:59:59Z", res.getHeader("Sunset"));
        assertNotNull(res.getHeader("Warning"));
        assertTrue(res.getHeader("Warning").contains("deprecated"));
        verify(mockMetrics).recordApiVersionRequest("v1", "DEPRECATED");
    }

    @Test
    @DisplayName(
            "Excluded paths (e.g. /actuator/health) should bypass version enforcement entirely")
    void testExcludedPaths() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.addHeader("X-API-Version", "v999"); // Unsupported, but path is excluded!
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(chainCalled.get());
        assertEquals(200, res.getStatus());
        verifyNoInteractions(mockMetrics);
    }

    @Test
    @DisplayName("DISABLED mode: Skips enforcement and passes through")
    void testDisabledMode() throws ServletException, IOException {
        properties.setEnforcementMode(EnforcementMode.DISABLED);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rides");
        req.addHeader("X-API-Version", "v999");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(chainCalled.get());
    }
}
