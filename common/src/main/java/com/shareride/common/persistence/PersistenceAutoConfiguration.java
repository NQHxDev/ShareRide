package com.shareride.common.persistence;

import org.hibernate.cfg.JdbcSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enterprise AutoConfiguration for relational persistence:
 * - Enables Spring Data JPA auditing with ShareRideAuditorAware
 * - Injects SQL statement inspection for OpenTelemetry trace correlation
 */
@AutoConfiguration
@ConditionalOnClass({ AuditorAware.class, EnableJpaAuditing.class })
@EnableJpaAuditing(auditorAwareRef = "shareRideAuditorAware")
public class PersistenceAutoConfiguration {

   @Bean
   @ConditionalOnMissingBean(name = "shareRideAuditorAware")
   public AuditorAware<String> shareRideAuditorAware() {
      return new ShareRideAuditorAware();
   }

   @Bean
   @ConditionalOnMissingBean(ShareRideStatementInspector.class)
   public ShareRideStatementInspector shareRideStatementInspector() {
      return new ShareRideStatementInspector();
   }

   @Bean
   public HibernatePropertiesCustomizer shareRideHibernatePropertiesCustomizer(ShareRideStatementInspector inspector) {
      return hibernateProperties -> hibernateProperties.put(
            JdbcSettings.STATEMENT_INSPECTOR,
            inspector);
   }

}
