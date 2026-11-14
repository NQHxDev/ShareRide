package com.shareride.notification.service;

import java.util.Locale;

public interface EmailMessageSourceService {

    String getMessage(String key, Object[] args, Locale locale);

    String getMessage(String key, Locale locale);

    Locale resolveLocale(String localeTag);
}
