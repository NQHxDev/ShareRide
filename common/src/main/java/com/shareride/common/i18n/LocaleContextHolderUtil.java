package com.shareride.common.i18n;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Utility for retrieving current thread Locale.
 */
public final class LocaleContextHolderUtil {

   private LocaleContextHolderUtil() {
   }

   public static Locale getLocale() {
      Locale current = LocaleContextHolder.getLocale();
      return current != null ? current : SupportedLocale.DEFAULT.getLocale();
   }
}
