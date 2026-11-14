package com.shareride.notification.service.impl;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.RenderedEmail;
import com.shareride.notification.service.EmailTemplateRegistry;
import com.shareride.notification.service.EmailTemplateRenderer;
import com.shareride.notification.service.EmailTemplateStrategy;
import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailTemplateRendererImpl implements EmailTemplateRenderer {

    private final Configuration freemarkerConfiguration;
    private final EmailTemplateRegistry templateRegistry;

    @Override
    public RenderedEmail render(NotificationType type, Locale locale, Map<String, Object> model) {
        String html = renderHtml(type, locale, model);
        String plainText = renderPlainText(type, locale, model);
        EmailTemplateStrategy strategy = templateRegistry.getStrategy(type);

        RenderedEmail email = new RenderedEmail();
        email.setHtmlBody(html);
        email.setPlainTextBody(plainText);
        email.setSubject(strategy.getSubjectKey());
        return email;
    }

    @Override
    public String renderHtml(NotificationType type, Locale locale, Map<String, Object> model) {
        try {
            EmailTemplateStrategy strategy = templateRegistry.getStrategy(type);
            Map<String, Object> enrichedModel = strategy.enrichModel(model, locale);

            Template template =
                    freemarkerConfiguration.getTemplate(strategy.getHtmlTemplatePath(), locale);

            StringWriter writer = new StringWriter();
            template.process(enrichedModel, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render HTML template", e);
        }
    }

    @Override
    public String renderPlainText(NotificationType type, Locale locale, Map<String, Object> model) {
        try {
            EmailTemplateStrategy strategy = templateRegistry.getStrategy(type);
            Map<String, Object> enrichedModel = strategy.enrichModel(model, locale);

            Template template =
                    freemarkerConfiguration.getTemplate(
                            strategy.getPlainTextTemplatePath(), locale);

            StringWriter writer = new StringWriter();
            template.process(enrichedModel, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render plain text template", e);
        }
    }
}
