package com.shareride.notification.service.impl;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.service.EmailTemplateRegistry;
import com.shareride.notification.service.EmailTemplateStrategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailTemplateRegistryImpl implements EmailTemplateRegistry {

    private final List<EmailTemplateStrategy> strategies;

    @Override
    public EmailTemplateStrategy getStrategy(NotificationType type) {
        return strategies.stream()
                .filter(s -> s.supports(type))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No template strategy found for type: " + type));
    }
}
