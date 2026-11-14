package com.shareride.notification.service.impl;

import com.shareride.notification.config.FreeMarkerMessageMethod;
import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.RenderedEmail;
import com.shareride.notification.service.EmailTemplateRegistry;
import com.shareride.notification.service.EmailTemplateRenderer;
import com.shareride.notification.service.EmailTemplateStrategy;
import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailTemplateRendererImpl implements EmailTemplateRenderer {

    private final Configuration freemarkerConfiguration;
    private final EmailTemplateRegistry templateRegistry;
    private final MessageSource messageSource;

    @Override
    public RenderedEmail render(NotificationType type, Locale locale, Map<String, Object> model) {
        EmailTemplateStrategy strategy = templateRegistry.getStrategy(type);

        strategy.validateParameters(model);

        String html = renderHtml(type, locale, model);
        String plainText = renderPlainText(type, locale, model);

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

            strategy.validateParameters(model);

            Map<String, Object> enrichedModel = strategy.enrichModel(model, locale);

            // Add msg() helper for body template
            enrichedModel.put("msg", new FreeMarkerMessageMethod(messageSource, locale));

            Template bodyTemplate =
                    freemarkerConfiguration.getTemplate(strategy.getHtmlTemplatePath(), locale);

            StringWriter bodyWriter = new StringWriter();
            bodyTemplate.process(enrichedModel, bodyWriter);
            String bodyContent = bodyWriter.toString();

            Map<String, Object> layoutModel = new HashMap<>();
            layoutModel.put("content", bodyContent);
            layoutModel.put("subject", strategy.getSubjectKey());
            layoutModel.put("locale", locale.getLanguage());
            // Add msg() helper for layout template
            layoutModel.put("msg", new FreeMarkerMessageMethod(messageSource, locale));

            Template layoutTemplate =
                    freemarkerConfiguration.getTemplate("layout/base_layout.ftl", locale);
            StringWriter finalWriter = new StringWriter();
            layoutTemplate.process(layoutModel, finalWriter);

            return finalWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render HTML template for type: " + type, e);
        }
    }

    @Override
    public String renderPlainText(NotificationType type, Locale locale, Map<String, Object> model) {
        try {
            EmailTemplateStrategy strategy = templateRegistry.getStrategy(type);

            strategy.validateParameters(model);

            Map<String, Object> enrichedModel = strategy.enrichModel(model, locale);

            // Add msg() helper for plain text template
            enrichedModel.put("msg", new FreeMarkerMessageMethod(messageSource, locale));

            Template template =
                    freemarkerConfiguration.getTemplate(
                            strategy.getPlainTextTemplatePath(), locale);

            StringWriter writer = new StringWriter();
            template.process(enrichedModel, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render plain text template for type: " + type, e);
        }
    }
}
