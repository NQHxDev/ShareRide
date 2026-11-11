package com.shareride.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.shareride.common.filter.CorrelationIdFilter;
import com.shareride.common.filter.HttpLoggingFilter;
import com.shareride.common.filter.UserContextFilter;
import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.security.aspect.SecurityAuthorizationAspect;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.LocaleResolver;

class CommonWebMvcAutoConfigurationTest {

    @Test
    void testCommonWebMvcAutoConfigurationBeans() {
        CommonWebMvcAutoConfiguration config = new CommonWebMvcAutoConfiguration();

        LocaleResolver localeResolver = config.localeResolver();
        assertNotNull(localeResolver);

        CorrelationIdFilter correlationFilter = config.correlationIdFilter();
        assertNotNull(correlationFilter);

        UserContextFilter userContextFilter = config.userContextFilter(null);
        assertNotNull(userContextFilter);

        HttpLoggingFilter httpLoggingFilter =
                config.httpLoggingFilter(new ShareRideLoggingProperties(), null, null);
        assertNotNull(httpLoggingFilter);

        SecurityAuthorizationAspect aspect = config.securityAuthorizationAspect();
        assertNotNull(aspect);

        var handler = config.globalExceptionHandler(null, null, null);
        assertNotNull(handler);
    }
}
