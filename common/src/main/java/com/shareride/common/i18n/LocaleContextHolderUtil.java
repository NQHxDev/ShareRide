package com.shareride.common.i18n;

import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/** Utility for retrieving current thread Locale. */
public final class LocaleContextHolderUtil {

    private LocaleContextHolderUtil() {}

    public static Locale getLocale() {
        Locale current = LocaleContextHolder.getLocale();
        return current != null ? current : SupportedLocale.DEFAULT.getLocale();
    }
}
