package com.shareride.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Custom locale resolver reading from Accept-Language or X-Lang header, with fallback to Vietnamese
 * (vi_VN).
 */
@Component("localeResolver")
public class HeaderLocaleResolver implements LocaleResolver {

    public static final String CUSTOM_LANG_HEADER = "X-Lang";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String langHeader = request.getHeader(CUSTOM_LANG_HEADER);
        if (langHeader == null || langHeader.isBlank()) {
            langHeader = request.getHeader("Accept-Language");
        }
        SupportedLocale supported = SupportedLocale.fromString(langHeader);
        return supported.getLocale();
    }

    @Override
    public void setLocale(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Locale locale) {
        // Stateless, headers are read-only per request
    }
}
