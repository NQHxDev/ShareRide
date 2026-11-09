package com.shareride.common.i18n;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * FreeMarker template based message resolver supporting dynamic placeholders and 4 languages: vi,
 * en, zh, ja.
 */
@Component
public class TemplateMessageSource {

    private static final Logger log = LoggerFactory.getLogger(TemplateMessageSource.class);

    private final Configuration freemarkerConfig;
    // Map: locale -> (key -> raw template string)
    private final Map<SupportedLocale, Map<String, String>> messageRegistry =
            new ConcurrentHashMap<>();
    // Cache for compiled FreeMarker templates
    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();

    public TemplateMessageSource() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_34);
        this.freemarkerConfig.setDefaultEncoding(StandardCharsets.UTF_8.name());
        this.freemarkerConfig.setLocalizedLookup(false);
    }

    @PostConstruct
    public void init() {
        loadTemplates();
        MessageResolverHolder.setTemplateMessageSource(this);
    }

    public synchronized void loadTemplates() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (SupportedLocale supportedLocale : SupportedLocale.values()) {
            Map<String, String> map =
                    messageRegistry.computeIfAbsent(
                            supportedLocale, k -> new ConcurrentHashMap<>());
            String pattern = "classpath*:messages/messages_" + supportedLocale.getCode() + ".ftl";
            try {
                Resource[] resources = resolver.getResources(pattern);
                for (Resource resource : resources) {
                    if (resource.exists() && resource.isReadable()) {
                        parseFtlResource(resource, map);
                    }
                }
                log.info(
                        "Loaded {} messages for locale [{}]",
                        map.size(),
                        supportedLocale.getCode());
            } catch (IOException e) {
                log.error("Failed to load messages for locale: {}", supportedLocale.getCode(), e);
            }
        }
    }

    private void parseFtlResource(Resource resource, Map<String, String> targetMap) {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            StringBuilder currentKey = new StringBuilder();
            StringBuilder currentValue = new StringBuilder();
            boolean readingMultiline = false;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (readingMultiline) {
                    if (trimmed.endsWith("\\")) {
                        currentValue.append("\n").append(trimmed, 0, trimmed.length() - 1);
                    } else {
                        currentValue.append("\n").append(trimmed);
                        targetMap.put(currentKey.toString(), currentValue.toString());
                        readingMultiline = false;
                        currentKey.setLength(0);
                        currentValue.setLength(0);
                    }
                    continue;
                }

                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if (val.endsWith("\\")) {
                        readingMultiline = true;
                        currentKey.append(key);
                        currentValue.append(val, 0, val.length() - 1);
                    } else {
                        targetMap.put(key, val);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not read message resource: {}", resource.getDescription(), e);
        }
    }

    public String getMessage(String code, Locale locale) {
        return getMessage(code, Collections.emptyMap(), locale);
    }

    public String getMessage(String code, Map<String, Object> params, Locale locale) {
        if (code == null) {
            return "";
        }
        SupportedLocale supportedLocale = SupportedLocale.fromLocale(locale);
        Map<String, String> localeMessages =
                messageRegistry.getOrDefault(supportedLocale, Collections.emptyMap());
        String rawTemplate = localeMessages.get(code);

        // Fallback to default (VI) then EN if not found
        if (rawTemplate == null && supportedLocale != SupportedLocale.DEFAULT) {
            rawTemplate =
                    messageRegistry
                            .getOrDefault(SupportedLocale.DEFAULT, Collections.emptyMap())
                            .get(code);
        }
        if (rawTemplate == null) {
            rawTemplate =
                    messageRegistry
                            .getOrDefault(SupportedLocale.EN, Collections.emptyMap())
                            .get(code);
        }

        if (rawTemplate == null) {
            return code;
        }

        if (params == null || params.isEmpty() || !rawTemplate.contains("${")) {
            return rawTemplate;
        }

        String cacheKey = supportedLocale.getCode() + ":" + code;
        try {
            Template template =
                    templateCache.computeIfAbsent(
                            cacheKey,
                            k -> {
                                try {
                                    return new Template(
                                            cacheKey,
                                            new StringReader(
                                                    localeMessages.getOrDefault(code, code)),
                                            freemarkerConfig);
                                } catch (IOException e) {
                                    throw new IllegalStateException(
                                            "Failed to parse FTL template for key: " + k, e);
                                }
                            });

            StringWriter out = new StringWriter();
            template.process(params, out);
            return out.toString();
        } catch (TemplateException | IOException e) {
            log.warn(
                    "FreeMarker render error for code '{}', params {}: {}",
                    code,
                    params,
                    e.getMessage());
            return rawTemplate;
        }
    }
}
