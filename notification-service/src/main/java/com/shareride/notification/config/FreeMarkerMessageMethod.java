package com.shareride.notification.config;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;

/**
 * FreeMarker custom method model that exposes Spring MessageSource as ${msg('key')} function in
 * templates.
 */
@RequiredArgsConstructor
public class FreeMarkerMessageMethod implements TemplateMethodModelEx {

    private final MessageSource messageSource;
    private final Locale locale;

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        if (arguments == null || arguments.isEmpty()) {
            throw new TemplateModelException(
                    "msg() requires at least one argument: the message key");
        }

        String key = arguments.get(0).toString();

        Object[] args = null;
        if (arguments.size() > 1) {
            args = new Object[arguments.size() - 1];
            for (int i = 1; i < arguments.size(); i++) {
                args[i - 1] = arguments.get(i).toString();
            }
        }

        String message = messageSource.getMessage(key, args, locale);
        return new SimpleScalar(message);
    }
}
