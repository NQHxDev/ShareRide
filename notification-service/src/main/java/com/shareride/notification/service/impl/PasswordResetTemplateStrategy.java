package com.shareride.notification.service.impl;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.service.EmailMessageSourceService;
import com.shareride.notification.service.EmailTemplateStrategy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetTemplateStrategy implements EmailTemplateStrategy {

    private final EmailMessageSourceService messageSourceService;

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.PASSWORD_RESET;
    }

    @Override
    public void validateParameters(Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (!params.containsKey("userName")) {
            throw new IllegalArgumentException("Missing required parameter: userName");
        }
        if (!params.containsKey("resetLink")) {
            throw new IllegalArgumentException("Missing required parameter: resetLink");
        }
        if (!params.containsKey("expiresInMinutes")) {
            throw new IllegalArgumentException("Missing required parameter: expiresInMinutes");
        }
    }

    @Override
    public String getSubjectKey() {
        return "email.password.reset.subject";
    }

    @Override
    public String getHtmlTemplatePath() {
        return "auth/password_reset_html.ftl";
    }

    @Override
    public String getPlainTextTemplatePath() {
        return "auth/password_reset_text.ftl";
    }

    @Override
    public Map<String, Object> enrichModel(Map<String, Object> params, Locale locale) {
        Map<String, Object> enriched = new HashMap<>(params);

        enriched.put(
                "greeting",
                messageSourceService.getMessage(
                        "email.greeting", new Object[] {params.get("userName")}, locale));
        enriched.put(
                "passwordResetTitle",
                messageSourceService.getMessage("email.password.reset.title", locale));
        enriched.put(
                "resetMessage",
                messageSourceService.getMessage("email.password.reset.message", locale));
        enriched.put(
                "linkLabel",
                messageSourceService.getMessage("email.password.reset.link.label", locale));
        enriched.put(
                "resetButton",
                messageSourceService.getMessage("email.password.reset.button", locale));
        enriched.put(
                "expiresMessage",
                messageSourceService.getMessage(
                        "email.password.reset.expires",
                        new Object[] {params.get("expiresInMinutes")},
                        locale));
        enriched.put(
                "securityTip",
                messageSourceService.getMessage("email.password.reset.security.tip", locale));
        enriched.put(
                "noRequestMessage",
                messageSourceService.getMessage("email.password.reset.no.request", locale));
        enriched.put("regards", messageSourceService.getMessage("email.regards", locale));
        enriched.put("teamName", messageSourceService.getMessage("email.team.name", locale));

        return enriched;
    }
}
