package com.shareride.common;

import com.shareride.common.exception.GlobalExceptionHandler;
import com.shareride.common.filter.TraceIdFilter;
import com.shareride.common.i18n.HeaderLocaleResolver;
import com.shareride.common.i18n.TemplateMessageSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Auto-configuration for common response, exception handling, and i18n support.
 */
@AutoConfiguration
public class CommonAutoConfiguration {

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
   public TraceIdFilter traceIdFilter() {
      return new TraceIdFilter();
   }

   @Bean
   @ConditionalOnMissingBean
   public GlobalExceptionHandler globalExceptionHandler(TemplateMessageSource templateMessageSource) {
      return new GlobalExceptionHandler(templateMessageSource);
   }

}
