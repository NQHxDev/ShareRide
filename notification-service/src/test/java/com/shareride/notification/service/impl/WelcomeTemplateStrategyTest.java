package com.shareride.notification.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.service.EmailMessageSourceService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WelcomeTemplateStrategy Unit Tests")
class WelcomeTemplateStrategyTest {

    @Mock private EmailMessageSourceService messageSourceService;

    private WelcomeTemplateStrategy welcomeTemplateStrategy;

    @BeforeEach
    void setUp() {
        welcomeTemplateStrategy = new WelcomeTemplateStrategy(messageSourceService);
    }

    @Test
    @DisplayName("supports() returns true for WELCOME notification type")
    void testSupportsWelcomeType() {
        assertTrue(welcomeTemplateStrategy.supports(NotificationType.WELCOME));
    }

    @Test
    @DisplayName("supports() returns false for non-WELCOME notification types")
    void testDoesNotSupportOtherTypes() {
        assertFalse(welcomeTemplateStrategy.supports(NotificationType.ACCOUNT_VERIFICATION));
        assertFalse(welcomeTemplateStrategy.supports(NotificationType.PASSWORD_RESET));
    }

    @Test
    @DisplayName("validateParameters() passes when userName is present")
    void testValidateParametersWithUserName() {
        final Map<String, Object> params = new HashMap<>();
        params.put("userName", "Nguyen Van A");

        welcomeTemplateStrategy.validateParameters(params);
    }

    @Test
    @DisplayName("validateParameters() throws exception when parameters are null")
    void testValidateParametersThrowsOnNull() {
        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> welcomeTemplateStrategy.validateParameters(null));

        assertEquals("Parameters cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("validateParameters() throws exception when userName is missing")
    void testValidateParametersThrowsOnMissingUserName() {
        final Map<String, Object> params = new HashMap<>();

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> welcomeTemplateStrategy.validateParameters(params));

        assertEquals("Missing required parameter: userName", exception.getMessage());
    }

    @Test
    @DisplayName("getSubjectKey() returns correct message key")
    void testGetSubjectKey() {
        assertEquals("email.welcome.subject", welcomeTemplateStrategy.getSubjectKey());
    }

    @Test
    @DisplayName("getHtmlTemplatePath() returns correct template path")
    void testGetHtmlTemplatePath() {
        assertEquals("auth/welcome_html.ftl", welcomeTemplateStrategy.getHtmlTemplatePath());
    }

    @Test
    @DisplayName("getPlainTextTemplatePath() returns correct template path")
    void testGetPlainTextTemplatePath() {
        assertEquals("auth/welcome_text.ftl", welcomeTemplateStrategy.getPlainTextTemplatePath());
    }

    @Test
    @DisplayName("enrichModel() adds Vietnamese localized messages to model")
    void testEnrichModelWithVietnameseLocale() {
        final Map<String, Object> params = new HashMap<>();
        params.put("userName", "Nguyen Van A");
        params.put("dashboardUrl", "https://shareride.com/dashboard");
        params.put("supportUrl", "https://shareride.com/support");

        when(messageSourceService.getMessage(
                        eq("email.welcome.greeting"),
                        any(Object[].class),
                        eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn("Chào Nguyen Van A");
        when(messageSourceService.getMessage(
                        eq("email.welcome.title"), eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn("Chào mừng bạn đến với ShareRide!");
        when(messageSourceService.getMessage(
                        eq("email.welcome.message"), eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn(
                        "Tài khoản của bạn đã được kích hoạt thành công. Hãy bắt đầu hành trình của"
                                + " bạn!");
        when(messageSourceService.getMessage(
                        eq("email.welcome.cta.dashboard"), eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn("Truy cập Dashboard");
        when(messageSourceService.getMessage(
                        eq("email.welcome.cta.support"), eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn("Liên hệ hỗ trợ");
        when(messageSourceService.getMessage(
                        eq("email.regards"), eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn("Trân trọng");
        when(messageSourceService.getMessage(
                        eq("email.team.name"), eq(Locale.forLanguageTag("vi-VN"))))
                .thenReturn("Đội ngũ ShareRide");

        final Map<String, Object> enriched =
                welcomeTemplateStrategy.enrichModel(params, Locale.forLanguageTag("vi-VN"));

        assertEquals("Nguyen Van A", enriched.get("userName"));
        assertEquals("https://shareride.com/dashboard", enriched.get("dashboardUrl"));
        assertEquals("https://shareride.com/support", enriched.get("supportUrl"));
        assertEquals("Chào Nguyen Van A", enriched.get("greeting"));
        assertEquals("Chào mừng bạn đến với ShareRide!", enriched.get("welcomeTitle"));
        assertEquals(
                "Tài khoản của bạn đã được kích hoạt thành công. Hãy bắt đầu hành trình của bạn!",
                enriched.get("welcomeMessage"));
        assertEquals("Truy cập Dashboard", enriched.get("ctaDashboard"));
        assertEquals("Liên hệ hỗ trợ", enriched.get("ctaSupport"));
        assertEquals("Trân trọng", enriched.get("regards"));
        assertEquals("Đội ngũ ShareRide", enriched.get("teamName"));
    }

    @Test
    @DisplayName("enrichModel() adds English localized messages to model")
    void testEnrichModelWithEnglishLocale() {
        final Map<String, Object> params = new HashMap<>();
        params.put("userName", "John Doe");

        when(messageSourceService.getMessage(
                        eq("email.welcome.greeting"),
                        any(Object[].class),
                        eq(Locale.forLanguageTag("en-US"))))
                .thenReturn("Hello John Doe");
        when(messageSourceService.getMessage(
                        eq("email.welcome.title"), eq(Locale.forLanguageTag("en-US"))))
                .thenReturn("Welcome to ShareRide!");
        when(messageSourceService.getMessage(
                        eq("email.welcome.message"), eq(Locale.forLanguageTag("en-US"))))
                .thenReturn(
                        "Your account has been successfully activated. Start your journey now!");
        when(messageSourceService.getMessage(
                        eq("email.welcome.cta.dashboard"), eq(Locale.forLanguageTag("en-US"))))
                .thenReturn("Go to Dashboard");
        when(messageSourceService.getMessage(
                        eq("email.welcome.cta.support"), eq(Locale.forLanguageTag("en-US"))))
                .thenReturn("Contact Support");
        when(messageSourceService.getMessage(
                        eq("email.regards"), eq(Locale.forLanguageTag("en-US"))))
                .thenReturn("Best regards");
        when(messageSourceService.getMessage(
                        eq("email.team.name"), eq(Locale.forLanguageTag("en-US"))))
                .thenReturn("ShareRide Team");

        final Map<String, Object> enriched =
                welcomeTemplateStrategy.enrichModel(params, Locale.forLanguageTag("en-US"));

        assertEquals("Hello John Doe", enriched.get("greeting"));
        assertEquals("Welcome to ShareRide!", enriched.get("welcomeTitle"));
        assertEquals(
                "Your account has been successfully activated. Start your journey now!",
                enriched.get("welcomeMessage"));
        assertEquals("Go to Dashboard", enriched.get("ctaDashboard"));
        assertEquals("Contact Support", enriched.get("ctaSupport"));
    }
}
