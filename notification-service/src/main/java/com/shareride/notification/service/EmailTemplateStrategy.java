package com.shareride.notification.service;

import com.shareride.notification.domain.NotificationType;
import java.util.Locale;
import java.util.Map;

public interface EmailTemplateStrategy {

    boolean supports(NotificationType type);

    void validateParameters(Map<String, Object> params);

    String getSubjectKey();

    String getHtmlTemplatePath();

    String getPlainTextTemplatePath();

    Map<String, Object> enrichModel(Map<String, Object> params, Locale locale);
}
