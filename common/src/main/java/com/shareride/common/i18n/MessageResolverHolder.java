package com.shareride.common.i18n;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Global static message resolver holder allowing BaseResponse and components
 * to resolve localized messages conveniently without injecting dependencies.
 */
public final class MessageResolverHolder {

   private static volatile TemplateMessageSource templateMessageSource;

   private MessageResolverHolder() {
   }

   public static void setTemplateMessageSource(TemplateMessageSource source) {
      templateMessageSource = source;
   }

   public static TemplateMessageSource getTemplateMessageSource() {
      return templateMessageSource;
   }

   public static String getMessage(String code) {
      return getMessage(code, Collections.emptyMap(), LocaleContextHolderUtil.getLocale());
   }

   public static String getMessage(String code, Locale locale) {
      return getMessage(code, Collections.emptyMap(), locale);
   }

   public static String getMessage(String code, Map<String, Object> params) {
      return getMessage(code, params, LocaleContextHolderUtil.getLocale());
   }

   public static String getMessage(String code, Map<String, Object> params, Locale locale) {
      if (templateMessageSource != null && code != null) {
         try {
            return templateMessageSource.getMessage(code, params, locale);
         } catch (Exception ignored) {
            // fallback
         }
      }
      return code != null ? code : "";
   }
}
