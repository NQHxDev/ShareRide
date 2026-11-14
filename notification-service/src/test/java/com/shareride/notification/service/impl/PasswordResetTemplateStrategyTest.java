package com.shareride.notification.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class PasswordResetTemplateStrategyTest {

    @Mock private EmailMessageSourceService messageSourceService;

    private PasswordResetTemplateStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PasswordResetTemplateStrategy(messageSourceService);
    }

    @Test
    @DisplayName("supports should return true only for PASSWORD_RESET")
    void testSupports() {
        assertTrue(strategy.supports(NotificationType.PASSWORD_RESET));
        assertFalse(strategy.supports(NotificationType.ACCOUNT_VERIFICATION));
    }

    @Test
    @DisplayName("validateParameters should validate required parameters")
    void testValidateParameters() {
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(null));

        Map<String, Object> params = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(params));

        params.put("userName", "Alice");
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(params));

        params.put("resetLink", "https://shareride.com/reset?token=xyz");
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(params));

        params.put("expiresInMinutes", 30);
        // Valid now
        strategy.validateParameters(params);
    }

    @Test
    @DisplayName("template paths and subject key should match expected constants")
    void testTemplatePathsAndSubjectKey() {
        assertEquals("email.password.reset.subject", strategy.getSubjectKey());
        assertEquals("auth/password_reset_html.ftl", strategy.getHtmlTemplatePath());
        assertEquals("auth/password_reset_text.ftl", strategy.getPlainTextTemplatePath());
    }

    @Test
    @DisplayName("enrichModel should populate all localized fields")
    void testEnrichModel() {
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "Alice");
        params.put("resetLink", "https://shareride.com/reset?token=xyz");
        params.put("expiresInMinutes", 30);

        Locale locale = Locale.ENGLISH;
        when(messageSourceService.getMessage(eq("email.greeting"), any(Object[].class), eq(locale)))
                .thenReturn("Hello Alice");
        when(messageSourceService.getMessage(eq("email.password.reset.title"), eq(locale)))
                .thenReturn("Reset Password");
        when(messageSourceService.getMessage(eq("email.password.reset.message"), eq(locale)))
                .thenReturn("Click below to reset");
        when(messageSourceService.getMessage(eq("email.password.reset.link.label"), eq(locale)))
                .thenReturn("Link:");
        when(messageSourceService.getMessage(eq("email.password.reset.button"), eq(locale)))
                .thenReturn("Reset");
        when(messageSourceService.getMessage(
                        eq("email.password.reset.expires"), any(Object[].class), eq(locale)))
                .thenReturn("Expires in 30 mins");
        when(messageSourceService.getMessage(eq("email.password.reset.security.tip"), eq(locale)))
                .thenReturn("Keep it secure");
        when(messageSourceService.getMessage(eq("email.password.reset.no.request"), eq(locale)))
                .thenReturn("Ignore if not requested");
        when(messageSourceService.getMessage(eq("email.regards"), eq(locale)))
                .thenReturn("Regards");
        when(messageSourceService.getMessage(eq("email.team.name"), eq(locale)))
                .thenReturn("ShareRide Team");

        Map<String, Object> enriched = strategy.enrichModel(params, locale);

        assertNotNull(enriched);
        assertEquals("Hello Alice", enriched.get("greeting"));
        assertEquals("Reset Password", enriched.get("passwordResetTitle"));
        assertEquals("https://shareride.com/reset?token=xyz", enriched.get("resetLink"));
        assertEquals("Regards", enriched.get("regards"));
    }
}
