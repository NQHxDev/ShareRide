package com.shareride.common.probe;

import com.shareride.common.probe.dependency.DatabaseHealthIndicator;
import com.shareride.common.probe.dependency.ExternalApiHealthIndicator;
import com.shareride.common.probe.dependency.ExternalDependencyRegistry;
import com.shareride.common.probe.dependency.MessageQueueHealthIndicator;
import com.shareride.common.probe.dependency.RedisHealthIndicator;
import com.shareride.common.probe.dependency.ShareRideDiskSpaceHealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Auto-configuration for Kubernetes health probes (startup, liveness,
 * readiness)
 * and resilient dependency health checks with timeout and circuit breaking.
 */
@AutoConfiguration
public class ProbeAutoConfiguration {

   @Bean
   @ConditionalOnMissingBean
   public StartupProbeHealthIndicator startupProbeHealthIndicator(
         @Value("${management.endpoint.health.startup.timeout:120}") long startupTimeoutSeconds) {
      return new StartupProbeHealthIndicator(startupTimeoutSeconds);
   }

   @Bean
   @ConditionalOnMissingBean
   public ServiceAvailabilityManager serviceAvailabilityManager(
         ApplicationAvailability availability,
         ApplicationEventPublisher eventPublisher,
         ObjectProvider<StartupProbeHealthIndicator> startupIndicatorProvider) {
      return new ServiceAvailabilityManager(availability, eventPublisher, startupIndicatorProvider);
   }

   @Bean
   @ConditionalOnMissingBean
   public ShareRideAppHealthIndicator shareRideAppHealthIndicator(
         ServiceAvailabilityManager availabilityManager,
         @Value("${spring.application.name:shareride-service}") String applicationName) {
      return new ShareRideAppHealthIndicator(availabilityManager, applicationName);
   }

   @Bean
   @ConditionalOnMissingBean(name = "databaseHealthIndicator")
   public DatabaseHealthIndicator databaseHealthIndicator(ObjectProvider<DataSource> dataSourceProvider) {
      return new DatabaseHealthIndicator(dataSourceProvider);
   }

   @Bean
   @ConditionalOnMissingBean(name = "redisHealthIndicator")
   public RedisHealthIndicator redisHealthIndicator(ApplicationContext applicationContext) {
      return new RedisHealthIndicator(applicationContext);
   }

   @Bean
   @ConditionalOnMissingBean(name = "messageQueueHealthIndicator")
   public MessageQueueHealthIndicator messageQueueHealthIndicator(ApplicationContext applicationContext) {
      return new MessageQueueHealthIndicator(applicationContext);
   }

   @Bean
   @ConditionalOnMissingBean
   public ExternalDependencyRegistry externalDependencyRegistry() {
      return new ExternalDependencyRegistry();
   }

   @Bean
   @ConditionalOnMissingBean(name = "externalApiHealthIndicator")
   public ExternalApiHealthIndicator externalApiHealthIndicator(ExternalDependencyRegistry registry) {
      return new ExternalApiHealthIndicator(registry);
   }

   @Bean
   @ConditionalOnMissingBean(name = "diskSpaceHealthIndicator")
   public ShareRideDiskSpaceHealthIndicator diskSpaceHealthIndicator(
         @Value("${management.health.diskspace.threshold:524288000}") long thresholdBytes) {
      return new ShareRideDiskSpaceHealthIndicator(new java.io.File("."), thresholdBytes);
   }
}
