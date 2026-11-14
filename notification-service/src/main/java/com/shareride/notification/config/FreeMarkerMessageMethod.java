package com.shareride.notification.config;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import java.util.Locale;
import org.springframework.context.MessageSource;

/**
 * FreeMarker custom method model factory that exposes Spring MessageSource as ${msg('key')}
 * function in templates without raw type warnings.
 */
public final class FreeMarkerMessageMethod {

    private FreeMarkerMessageMethod() {}

    /**
     * Creates a {@link TemplateMethodModelEx} instance via lambda, eliminating raw type references.
     *
     * @param messageSource the Spring message source
     * @param locale the target locale
     * @return the FreeMarker method model
     */
    public static TemplateMethodModelEx of(MessageSource messageSource, Locale locale) {
        return arguments -> {
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
        };
    }
}
