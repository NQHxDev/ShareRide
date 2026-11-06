package com.shareride.common.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.CommonAutoConfiguration;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.telemetry.TelemetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot AutoConfiguration for ShareRide API Versioning & Enforcement Strategy.
 */
@AutoConfiguration(before = org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class, after = {TelemetryAutoConfiguration.class, CommonAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ShareRideVersioningProperties.class)
public class ApiVersioningAutoConfiguration implements WebMvcConfigurer {

   private final ObjectProvider<ApiVersionInterceptor> interceptorProvider;

   public ApiVersioningAutoConfiguration(ObjectProvider<ApiVersionInterceptor> interceptorProvider) {
      this.interceptorProvider = interceptorProvider;
   }


   @Bean
   @ConditionalOnMissingBean
   public ApiVersionResolver apiVersionResolver(ShareRideVersioningProperties properties) {
      return new DefaultApiVersionResolver(properties);
   }

   @Bean
   @ConditionalOnMissingBean
   public ApiVersionEnforcementFilter apiVersionEnforcementFilter(
         ShareRideVersioningProperties properties,
         ApiVersionResolver resolver,
         ObjectProvider<TemplateMessageSource> messageSourceProvider,
         ObjectProvider<ShareRideMetrics> metricsProvider,
         ObjectProvider<ObjectMapper> objectMapperProvider) {
      return new ApiVersionEnforcementFilter(properties, resolver, messageSourceProvider, metricsProvider, objectMapperProvider);
   }

   @Bean
   @ConditionalOnMissingBean
   public ApiVersionInterceptor apiVersionInterceptor(ShareRideVersioningProperties properties) {
      return new ApiVersionInterceptor(properties);
   }

   @Bean
   @ConditionalOnMissingBean(WebMvcRegistrations.class)
   @ConditionalOnProperty(prefix = "shareride.versioning", name = "routing-condition-enabled", havingValue = "true", matchIfMissing = true)
   public WebMvcRegistrations apiVersionWebMvcRegistrations() {
      return new ApiVersionWebMvcRegistrations();
   }

   @Override
   public void addInterceptors(InterceptorRegistry registry) {
      ApiVersionInterceptor interceptor = interceptorProvider.getIfAvailable();
      if (interceptor != null) {
         registry.addInterceptor(interceptor);
      }
   }
}
