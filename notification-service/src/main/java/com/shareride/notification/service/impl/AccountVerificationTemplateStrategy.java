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
public class AccountVerificationTemplateStrategy implements EmailTemplateStrategy {

    private final EmailMessageSourceService messageSourceService;

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.ACCOUNT_VERIFICATION;
    }

    @Override
    public void validateParameters(Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (!params.containsKey("userName")) {
            throw new IllegalArgumentException("Missing required parameter: userName");
        }
        if (!params.containsKey("verificationCode")) {
            throw new IllegalArgumentException("Missing required parameter: verificationCode");
        }
        if (!params.containsKey("expiresInMinutes")) {
            throw new IllegalArgumentException("Missing required parameter: expiresInMinutes");
        }
    }

    @Override
    public String getSubjectKey() {
        return "email.verification.subject";
    }

    @Override
    public String getHtmlTemplatePath() {
        return "auth/verification_html.ftl";
    }

    @Override
    public String getPlainTextTemplatePath() {
        return "auth/verification_text.ftl";
    }

    @Override
    public Map<String, Object> enrichModel(Map<String, Object> params, Locale locale) {
        Map<String, Object> enriched = new HashMap<>(params);

        enriched.put(
                "greeting",
                messageSourceService.getMessage(
                        "email.greeting", new Object[] {params.get("userName")}, locale));
        enriched.put(
                "emailVerificationTitle",
                messageSourceService.getMessage("email.verification.title", locale));
        enriched.put(
                "verificationMessage",
                messageSourceService.getMessage("email.verification.message", locale));
        enriched.put(
                "codeLabel",
                messageSourceService.getMessage("email.verification.code.label", locale));
        enriched.put(
                "expiresMessage",
                messageSourceService.getMessage(
                        "email.verification.expires",
                        new Object[] {params.get("expiresInMinutes")},
                        locale));
        enriched.put(
                "securityWarning",
                messageSourceService.getMessage("email.security.warning", locale));
        enriched.put(
                "noShareCode",
                messageSourceService.getMessage("email.security.no.share.code", locale));
        enriched.put(
                "noRequestMessage",
                messageSourceService.getMessage("email.verification.no.request", locale));
        enriched.put("regards", messageSourceService.getMessage("email.regards", locale));
        enriched.put("teamName", messageSourceService.getMessage("email.team.name", locale));

        return enriched;
    }
}
