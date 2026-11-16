package com.shareride.notification.service.impl;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.service.EmailMessageSourceService;
import com.shareride.notification.service.EmailTemplateStrategy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Template strategy cho WELCOME email được dispatch khi account được activated. Enriches model với
 * Vietnamese/English localized greeting, CTA buttons, và URLs.
 */
@Component
@RequiredArgsConstructor
public class WelcomeTemplateStrategy implements EmailTemplateStrategy {

    private final EmailMessageSourceService messageSourceService;

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.WELCOME;
    }

    @Override
    public void validateParameters(Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (!params.containsKey("userName")) {
            throw new IllegalArgumentException("Missing required parameter: userName");
        }
    }

    @Override
    public String getSubjectKey() {
        return "email.welcome.subject";
    }

    @Override
    public String getHtmlTemplatePath() {
        return "auth/welcome_html.ftl";
    }

    @Override
    public String getPlainTextTemplatePath() {
        return "auth/welcome_text.ftl";
    }

    @Override
    public Map<String, Object> enrichModel(Map<String, Object> params, Locale locale) {
        Map<String, Object> enriched = new HashMap<>(params);

        enriched.put(
                "greeting",
                messageSourceService.getMessage(
                        "email.welcome.greeting", new Object[] {params.get("userName")}, locale));
        enriched.put(
                "welcomeTitle", messageSourceService.getMessage("email.welcome.title", locale));
        enriched.put(
                "welcomeMessage", messageSourceService.getMessage("email.welcome.message", locale));
        enriched.put(
                "ctaDashboard",
                messageSourceService.getMessage("email.welcome.cta.dashboard", locale));
        enriched.put(
                "ctaSupport", messageSourceService.getMessage("email.welcome.cta.support", locale));
        enriched.put("regards", messageSourceService.getMessage("email.regards", locale));
        enriched.put("teamName", messageSourceService.getMessage("email.team.name", locale));

        return enriched;
    }
}
