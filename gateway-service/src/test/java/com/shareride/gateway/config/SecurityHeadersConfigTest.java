package com.shareride.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.gateway.constant.GatewayConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("SecurityHeadersConfig Unit Tests")
class SecurityHeadersConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityHeadersConfig.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should initialize with default OWASP security headers")
    void shouldInitializeWithDefaultSecurityHeaders() {
        SecurityHeadersConfig config = new SecurityHeadersConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isOverrideExisting()).isFalse();
        assertThat(config.getContentTypeOptions())
                .isEqualTo(GatewayConstants.DEFAULT_X_CONTENT_TYPE_OPTIONS);
        assertThat(config.getFrameOptions()).isEqualTo(GatewayConstants.DEFAULT_X_FRAME_OPTIONS);
        assertThat(config.getXssProtection()).isEqualTo(GatewayConstants.DEFAULT_X_XSS_PROTECTION);
        assertThat(config.getStrictTransportSecurity())
                .isEqualTo(GatewayConstants.DEFAULT_STRICT_TRANSPORT_SECURITY);
        assertThat(config.getContentSecurityPolicy())
                .isEqualTo(GatewayConstants.DEFAULT_CONTENT_SECURITY_POLICY);
        assertThat(config.getReferrerPolicy()).isEqualTo(GatewayConstants.DEFAULT_REFERRER_POLICY);
        assertThat(config.getPermissionsPolicy())
                .isEqualTo(GatewayConstants.DEFAULT_PERMISSIONS_POLICY);
        assertThat(config.getPermittedCrossDomainPolicies())
                .isEqualTo(GatewayConstants.DEFAULT_X_PERMITTED_CROSS_DOMAIN_POLICIES);
        assertThat(config.getCacheControl()).isNull();
        assertThat(config.getPragma()).isNull();
        assertThat(config.getExpires()).isNull();
    }

    @Test
    @DisplayName("Should bind custom properties from Spring Environment")
    void shouldBindCustomProperties() {
        contextRunner
                .withPropertyValues(
                        "shareride.gateway.security.headers.enabled=false",
                        "shareride.gateway.security.headers.override-existing=true",
                        "shareride.gateway.security.headers.content-type-options=nosniff",
                        "shareride.gateway.security.headers.frame-options=SAMEORIGIN",
                        "shareride.gateway.security.headers.xss-protection=1; mode=block",
                        "shareride.gateway.security.headers.strict-transport-security=max-age=63072000",
                        "shareride.gateway.security.headers.content-security-policy=default-src"
                                + " 'none'",
                        "shareride.gateway.security.headers.referrer-policy=no-referrer",
                        "shareride.gateway.security.headers.permissions-policy=camera=()",
                        "shareride.gateway.security.headers.permitted-cross-domain-policies=master-only",
                        "shareride.gateway.security.headers.cache-control=no-store",
                        "shareride.gateway.security.headers.pragma=no-cache",
                        "shareride.gateway.security.headers.expires=0")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(SecurityHeadersConfig.class);
                            SecurityHeadersConfig config =
                                    context.getBean(SecurityHeadersConfig.class);

                            assertThat(config.isEnabled()).isFalse();
                            assertThat(config.isOverrideExisting()).isTrue();
                            assertThat(config.getContentTypeOptions()).isEqualTo("nosniff");
                            assertThat(config.getFrameOptions()).isEqualTo("SAMEORIGIN");
                            assertThat(config.getXssProtection()).isEqualTo("1; mode=block");
                            assertThat(config.getStrictTransportSecurity())
                                    .isEqualTo("max-age=63072000");
                            assertThat(config.getContentSecurityPolicy())
                                    .isEqualTo("default-src 'none'");
                            assertThat(config.getReferrerPolicy()).isEqualTo("no-referrer");
                            assertThat(config.getPermissionsPolicy()).isEqualTo("camera=()");
                            assertThat(config.getPermittedCrossDomainPolicies())
                                    .isEqualTo("master-only");
                            assertThat(config.getCacheControl()).isEqualTo("no-store");
                            assertThat(config.getPragma()).isEqualTo("no-cache");
                            assertThat(config.getExpires()).isEqualTo("0");
                        });
    }

    @Test
    @DisplayName("Should allow mutation via setters")
    void shouldAllowMutationViaSetters() {
        SecurityHeadersConfig config = new SecurityHeadersConfig();

        config.setEnabled(false);
        config.setOverrideExisting(true);
        config.setContentTypeOptions("custom-options");
        config.setFrameOptions("SAMEORIGIN");
        config.setXssProtection("1");
        config.setStrictTransportSecurity("max-age=1000");
        config.setContentSecurityPolicy("default-src 'self'");
        config.setReferrerPolicy("origin");
        config.setPermissionsPolicy("usb=()");
        config.setPermittedCrossDomainPolicies("by-content-type");
        config.setCacheControl("private");
        config.setPragma("custom-pragma");
        config.setExpires("Thu, 01 Dec 1994 16:00:00 GMT");

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.isOverrideExisting()).isTrue();
        assertThat(config.getContentTypeOptions()).isEqualTo("custom-options");
        assertThat(config.getFrameOptions()).isEqualTo("SAMEORIGIN");
        assertThat(config.getXssProtection()).isEqualTo("1");
        assertThat(config.getStrictTransportSecurity()).isEqualTo("max-age=1000");
        assertThat(config.getContentSecurityPolicy()).isEqualTo("default-src 'self'");
        assertThat(config.getReferrerPolicy()).isEqualTo("origin");
        assertThat(config.getPermissionsPolicy()).isEqualTo("usb=()");
        assertThat(config.getPermittedCrossDomainPolicies()).isEqualTo("by-content-type");
        assertThat(config.getCacheControl()).isEqualTo("private");
        assertThat(config.getPragma()).isEqualTo("custom-pragma");
        assertThat(config.getExpires()).isEqualTo("Thu, 01 Dec 1994 16:00:00 GMT");
    }
}
