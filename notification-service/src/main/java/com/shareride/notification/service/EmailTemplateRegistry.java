package com.shareride.notification.service;

import com.shareride.notification.domain.NotificationType;

public interface EmailTemplateRegistry {
    EmailTemplateStrategy getStrategy(NotificationType type);
}
