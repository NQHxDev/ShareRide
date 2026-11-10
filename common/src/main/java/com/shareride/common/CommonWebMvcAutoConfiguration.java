package com.shareride.common;

import com.shareride.common.exception.GlobalExceptionHandler;
import com.shareride.common.filter.CorrelationIdFilter;
import com.shareride.common.filter.HttpLoggingFilter;
import com.shareride.common.filter.UserContextFilter;
import com.shareride.common.i18n.HeaderLocaleResolver;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.sampling.LogSampler;
import com.shareride.common.metrics.ShareRideMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Auto-configuration for Servlet-based Web MVC microservices.
 *
 * <p>Registers HTTP locale resolution, correlation and logging servlet filters, and global REST
 * exception handling. Decoupled from core {@link CommonAutoConfiguration} to ensure reactive
 * microservices (e.g. API Gateway) can load common logging and telemetry without Web MVC classpath
 * conflicts.
 */
@AutoConfiguration(after = CommonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.LocaleResolver")
public class CommonWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        return new HeaderLocaleResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpLoggingFilter httpLoggingFilter(
            ShareRideLoggingProperties loggingProperties,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<LogSampler> samplerProvider) {
        return new HttpLoggingFilter(loggingProperties, metricsProvider, samplerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(
            TemplateMessageSource templateMessageSource,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        return new GlobalExceptionHandler(
                templateMessageSource, metricsProvider, auditLogServiceProvider);
    }
}
