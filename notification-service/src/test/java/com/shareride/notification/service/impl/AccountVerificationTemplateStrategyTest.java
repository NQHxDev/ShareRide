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
class AccountVerificationTemplateStrategyTest {

    @Mock private EmailMessageSourceService messageSourceService;

    private AccountVerificationTemplateStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AccountVerificationTemplateStrategy(messageSourceService);
    }

    @Test
    @DisplayName("supports should return true only for ACCOUNT_VERIFICATION")
    void testSupports() {
        assertTrue(strategy.supports(NotificationType.ACCOUNT_VERIFICATION));
        assertFalse(strategy.supports(NotificationType.PASSWORD_RESET));
    }

    @Test
    @DisplayName("validateParameters should validate required parameters")
    void testValidateParameters() {
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(null));

        Map<String, Object> params = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(params));

        params.put("userName", "John");
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(params));

        params.put("verificationCode", "123456");
        assertThrows(IllegalArgumentException.class, () -> strategy.validateParameters(params));

        params.put("expiresInMinutes", 15);
        // Valid now
        strategy.validateParameters(params);
    }

    @Test
    @DisplayName("template paths and subject key should match expected constants")
    void testTemplatePathsAndSubjectKey() {
        assertEquals("email.verification.subject", strategy.getSubjectKey());
        assertEquals("auth/verification_html.ftl", strategy.getHtmlTemplatePath());
        assertEquals("auth/verification_text.ftl", strategy.getPlainTextTemplatePath());
    }

    @Test
    @DisplayName("enrichModel should populate all localized fields")
    void testEnrichModel() {
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "John");
        params.put("verificationCode", "123456");
        params.put("expiresInMinutes", 15);

        Locale locale = Locale.ENGLISH;
        when(messageSourceService.getMessage(eq("email.greeting"), any(Object[].class), eq(locale)))
                .thenReturn("Hello John");
        when(messageSourceService.getMessage(eq("email.verification.title"), eq(locale)))
                .thenReturn("Verify Email");
        when(messageSourceService.getMessage(eq("email.verification.message"), eq(locale)))
                .thenReturn("Your code is:");
        when(messageSourceService.getMessage(eq("email.verification.code.label"), eq(locale)))
                .thenReturn("Code:");
        when(messageSourceService.getMessage(
                        eq("email.verification.expires"), any(Object[].class), eq(locale)))
                .thenReturn("Expires in 15 mins");
        when(messageSourceService.getMessage(eq("email.security.warning"), eq(locale)))
                .thenReturn("Warning");
        when(messageSourceService.getMessage(eq("email.security.no.share.code"), eq(locale)))
                .thenReturn("Do not share");
        when(messageSourceService.getMessage(eq("email.verification.no.request"), eq(locale)))
                .thenReturn("Ignore if not you");
        when(messageSourceService.getMessage(eq("email.regards"), eq(locale)))
                .thenReturn("Regards");
        when(messageSourceService.getMessage(eq("email.team.name"), eq(locale)))
                .thenReturn("ShareRide Team");

        Map<String, Object> enriched = strategy.enrichModel(params, locale);

        assertNotNull(enriched);
        assertEquals("Hello John", enriched.get("greeting"));
        assertEquals("Verify Email", enriched.get("emailVerificationTitle"));
        assertEquals("123456", enriched.get("verificationCode"));
        assertEquals("Regards", enriched.get("regards"));
    }
}
