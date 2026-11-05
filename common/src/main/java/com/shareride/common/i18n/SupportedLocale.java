package com.shareride.common.i18n;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;

/**
 * 4 default supported languages in ShareRide: VN, EN, CN, JP.
 */
@Getter
public enum SupportedLocale {

   VI("vi", Locale.forLanguageTag("vi")),
   EN("en", Locale.ENGLISH),
   ZH("zh", Locale.SIMPLIFIED_CHINESE),
   JA("ja", Locale.JAPANESE);

   private final String code;
   private final Locale locale;

   SupportedLocale(String code, Locale locale) {
      this.code = code;
      this.locale = locale;
   }

   public static final SupportedLocale DEFAULT = VI;

   public static SupportedLocale fromString(String lang) {
      if (lang == null || lang.isBlank()) {
         return DEFAULT;
      }
      String clean = lang.trim().toLowerCase();
      /*
       * Match prefix like:
       * 'en-US' -> 'en',
       * 'zh-CN' -> 'zh',
       * 'ja-JP' -> 'ja',
       * 'vi-VN' -> 'vi'
       */
      return Arrays.stream(values())
            .filter(sl -> clean.startsWith(sl.code))
            .findFirst()
            .orElse(DEFAULT);
   }

   public static SupportedLocale fromLocale(Locale locale) {
      if (locale == null) {
         return DEFAULT;
      }
      return fromString(locale.getLanguage());
   }

}
