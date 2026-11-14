package com.shareride.notification.service.impl;

import com.shareride.common.i18n.SupportedLocale;
import com.shareride.notification.service.EmailMessageSourceService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailMessageSourceServiceImpl implements EmailMessageSourceService {

    private final MessageSource messageSource;

    @Override
    public String getMessage(String key, Object[] args, Locale locale) {
        Locale resolvedLocale = resolveLocale(locale.toLanguageTag());
        return messageSource.getMessage(key, args, resolvedLocale);
    }

    @Override
    public String getMessage(String key, Locale locale) {
        return getMessage(key, null, locale);
    }

    @Override
    public Locale resolveLocale(String localeTag) {
        if (localeTag == null || localeTag.isBlank()) {
            return SupportedLocale.VI.getLocale();
        }

        SupportedLocale supportedLocale = SupportedLocale.fromString(localeTag);
        return supportedLocale.getLocale();
    }
}
