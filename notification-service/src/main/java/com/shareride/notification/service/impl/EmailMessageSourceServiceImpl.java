package com.shareride.notification.service.impl;

import com.shareride.common.i18n.SupportedLocale;
import com.shareride.notification.service.EmailMessageSourceService;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailMessageSourceServiceImpl implements EmailMessageSourceService {

    private final MessageSource messageSource;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    @Override
    public String formatCurrency(double amount, Locale locale) {
        Locale resolvedLocale = resolveLocale(locale.toLanguageTag());

        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(resolvedLocale);

        // Use VND currency for Vietnamese locale
        if (resolvedLocale.getLanguage().equals("vi")) {
            currencyFormatter.setCurrency(Currency.getInstance("VND"));
        }

        return currencyFormatter.format(amount);
    }

    @Override
    public String formatDateTime(Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }

        Locale resolvedLocale = resolveLocale(locale.toLanguageTag());

        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(resolvedLocale)
                        .withZone(DEFAULT_ZONE);

        return formatter.format(instant);
    }

    @Override
    public String formatDate(Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }

        Locale resolvedLocale = resolveLocale(locale.toLanguageTag());

        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(resolvedLocale)
                        .withZone(DEFAULT_ZONE);

        return formatter.format(instant);
    }
}
