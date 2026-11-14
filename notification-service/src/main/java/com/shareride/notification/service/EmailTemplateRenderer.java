package com.shareride.notification.service;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.RenderedEmail;
import java.util.Locale;
import java.util.Map;

public interface EmailTemplateRenderer {

    RenderedEmail render(NotificationType type, Locale locale, Map<String, Object> model);

    String renderHtml(NotificationType type, Locale locale, Map<String, Object> model);

    String renderPlainText(NotificationType type, Locale locale, Map<String, Object> model);
}
