package com.shareride.notification.service;

import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.dto.NotificationStatusResponse;
import java.util.UUID;

public interface NotificationService {

    UUID dispatchNotification(NotificationDispatchRequest request);

    NotificationStatusResponse getNotificationStatus(UUID notificationId);
}
