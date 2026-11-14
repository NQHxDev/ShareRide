package com.shareride.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.notification.config.I18nConfig;
import com.shareride.notification.service.impl.EmailMessageSourceServiceImpl;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

class EmailI18nResolutionTest {

    private EmailMessageSourceService emailMessageSourceService;

    @BeforeEach
    void setUp() {
        I18nConfig i18nConfig = new I18nConfig();
        MessageSource messageSource = i18nConfig.messageSource();
        emailMessageSourceService = new EmailMessageSourceServiceImpl(messageSource);
    }

    @Test
    void shouldResolveVietnameseLocale() {
        Locale locale = Locale.forLanguageTag("vi-VN");
        String message =
                emailMessageSourceService.getMessage(
                        "email.greeting", new Object[] {"Nguyễn Văn A"}, locale);

        assertThat(message).contains("Xin chào").contains("Nguyễn Văn A");
    }

    @Test
    void shouldResolveEnglishLocale() {
        Locale locale = Locale.forLanguageTag("en-US");
        String message =
                emailMessageSourceService.getMessage(
                        "email.greeting", new Object[] {"John"}, locale);

        assertThat(message).contains("Hello").contains("John");
    }

    @Test
    void shouldFallbackToVietnameseForUnsupportedLocale() {
        Locale locale = Locale.forLanguageTag("fr-FR");
        String message =
                emailMessageSourceService.getMessage(
                        "email.greeting", new Object[] {"Test User"}, locale);

        assertThat(message).contains("Xin chào").contains("Test User");
    }

    @Test
    void shouldResolveMessageKeysWithParameters() {
        Locale viLocale = Locale.forLanguageTag("vi");
        String viMessage =
                emailMessageSourceService.getMessage(
                        "email.verification.expires", new Object[] {15}, viLocale);

        assertThat(viMessage).contains("15");
        assertThat(viMessage).containsAnyOf("phút", "15 phút");

        Locale enLocale = Locale.forLanguageTag("en");
        String enMessage =
                emailMessageSourceService.getMessage(
                        "email.verification.expires", new Object[] {15}, enLocale);

        assertThat(enMessage).contains("15");
        assertThat(enMessage).containsAnyOf("minutes", "15 minutes");
    }

    @Test
    void shouldHandleLocaleNormalization() {
        Locale viVN = emailMessageSourceService.resolveLocale("vi-VN");
        Locale vi = emailMessageSourceService.resolveLocale("vi");

        assertThat(viVN).isEqualTo(vi);
        assertThat(viVN.getLanguage()).isEqualTo("vi");
    }

    @Test
    void shouldFormatCurrencyInVietnamese() {
        Locale viLocale = Locale.forLanguageTag("vi-VN");
        String formatted = emailMessageSourceService.formatCurrency(250000, viLocale);

        assertThat(formatted).contains("250");
        assertThat(formatted).containsAnyOf("₫", "VND");
    }

    @Test
    void shouldFormatCurrencyInEnglish() {
        Locale enLocale = Locale.forLanguageTag("en-US");
        String formatted = emailMessageSourceService.formatCurrency(250000, enLocale);

        assertThat(formatted).contains("250");
    }

    @Test
    void shouldFormatDateTimeInVietnamese() {
        Locale viLocale = Locale.forLanguageTag("vi-VN");
        Instant instant = Instant.parse("2026-11-14T08:30:00Z");
        String formatted = emailMessageSourceService.formatDateTime(instant, viLocale);

        assertThat(formatted).isNotEmpty();
        assertThat(formatted).containsAnyOf("2026", "11", "14");
    }

    @Test
    void shouldFormatDateTimeInEnglish() {
        Locale enLocale = Locale.forLanguageTag("en-US");
        Instant instant = Instant.parse("2026-11-14T08:30:00Z");
        String formatted = emailMessageSourceService.formatDateTime(instant, enLocale);

        assertThat(formatted).isNotEmpty();
        assertThat(formatted).containsAnyOf("2026", "Nov", "14");
    }

    @Test
    void shouldFormatDateInVietnamese() {
        Locale viLocale = Locale.forLanguageTag("vi-VN");
        Instant instant = Instant.parse("2026-11-14T08:30:00Z");
        String formatted = emailMessageSourceService.formatDate(instant, viLocale);

        assertThat(formatted).isNotEmpty();
        assertThat(formatted).contains("2026");
    }

    @Test
    void shouldHandleNullInstantInFormatting() {
        Locale viLocale = Locale.forLanguageTag("vi-VN");
        String formatted = emailMessageSourceService.formatDateTime(null, viLocale);

        assertThat(formatted).isEmpty();
    }
}
