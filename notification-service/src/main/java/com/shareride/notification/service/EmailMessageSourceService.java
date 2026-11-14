package com.shareride.notification.service;

import java.time.Instant;
import java.util.Locale;

public interface EmailMessageSourceService {

    String getMessage(String key, Object[] args, Locale locale);

    String getMessage(String key, Locale locale);

    Locale resolveLocale(String localeTag);

    String formatCurrency(double amount, Locale locale);

    String formatDateTime(Instant instant, Locale locale);

    String formatDate(Instant instant, Locale locale);
}
