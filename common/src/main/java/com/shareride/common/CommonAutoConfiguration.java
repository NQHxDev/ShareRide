package com.shareride.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.context.ShareRideTaskDecorator;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for common JSON mapping, environment metadata resolution, i18n message
 * templates, security audit logging, business event publishing, logging metrics, and logging
 * properties.
 *
 * <p>Web MVC Servlet specific beans are configured in {@link CommonWebMvcAutoConfiguration}.
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
    public ShareRideTaskDecorator shareRideTaskDecorator() {
        return new ShareRideTaskDecorator();
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
