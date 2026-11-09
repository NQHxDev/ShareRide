package com.shareride.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.context.ShareRideTaskDecorator;
import com.shareride.common.exception.GlobalExceptionHandler;
import com.shareride.common.filter.CorrelationIdFilter;
import com.shareride.common.filter.HttpLoggingFilter;
import com.shareride.common.filter.UserContextFilter;
import com.shareride.common.i18n.HeaderLocaleResolver;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.logging.EnvironmentContext;
import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.DefaultAuditLogService;
import com.shareride.common.logging.event.BusinessEventLogger;
import com.shareride.common.logging.metrics.LoggingMetrics;
import com.shareride.common.logging.sampling.LogSampler;
import com.shareride.common.metrics.ShareRideMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Auto-configuration for common response formatting, exception handling, i18n, context propagation,
 * security audit logging, business event publishing, and structured HTTP access logging.
 */
@AutoConfiguration
@EnableConfigurationProperties(ShareRideLoggingProperties.class)
public class CommonAutoConfiguration {

    private final Environment environment;

    public CommonAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void initEnvironmentMetadata() {
        if (environment != null) {
            String appName =
                    environment.getProperty("spring.application.name", "shareride-service");
            String appVersion = environment.getProperty("app.version", "1.0.0");
            String[] activeProfiles = environment.getActiveProfiles();
            String env = (activeProfiles.length > 0) ? activeProfiles[0] : "development";
            EnvironmentContext.initialize(appName, appVersion, env);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateMessageSource templateMessageSource() {
        return new TemplateMessageSource();
    }

    @Bean
    @ConditionalOnMissingBean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        return new HeaderLocaleResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public LogSampler logSampler(ShareRideLoggingProperties properties) {
        return new LogSampler(properties.getInfoSampleRate(), properties.getDebugSampleRate());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogService auditLogService() {
        return new DefaultAuditLogService();
    }

    @Bean
    @ConditionalOnMissingBean
    public BusinessEventLogger businessEventLogger() {
        return new BusinessEventLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public HttpLoggingFilter httpLoggingFilter(
            ShareRideLoggingProperties loggingProperties,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<LogSampler> samplerProvider) {
        return new HttpLoggingFilter(loggingProperties, metricsProvider, samplerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public ShareRideTaskDecorator shareRideTaskDecorator() {
        return new ShareRideTaskDecorator();
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

    @Bean
    @ConditionalOnMissingBean
    public LoggingMetricsInitializer loggingMetricsInitializer(
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LoggingMetricsInitializer(metricsProvider, meterRegistryProvider);
    }

    public static class LoggingMetricsInitializer {
        public LoggingMetricsInitializer(
                ObjectProvider<ShareRideMetrics> metricsProvider,
                ObjectProvider<MeterRegistry> meterRegistryProvider) {
            if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
                LoggingMetrics.setMetrics(metricsProvider.getIfAvailable());
            } else if (meterRegistryProvider != null
                    && meterRegistryProvider.getIfAvailable() != null) {
                LoggingMetrics.setMeterRegistry(meterRegistryProvider.getIfAvailable());
            }
        }
    }
}
