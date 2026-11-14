package com.shareride.notification.constant;

public final class NotificationResponseCode {

    private NotificationResponseCode() {}

    public static final String NOTIFICATION_SENT_SUCCESS = "NOTIFICATION_SENT_SUCCESS";
    public static final String NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";
    public static final String NOTIFICATION_DISPATCH_FAILED = "NOTIFICATION_DISPATCH_FAILED";
    public static final String INVALID_NOTIFICATION_TYPE = "INVALID_NOTIFICATION_TYPE";
    public static final String INVALID_RECIPIENT_EMAIL = "INVALID_RECIPIENT_EMAIL";
    public static final String MISSING_REQUIRED_PARAMETERS = "MISSING_REQUIRED_PARAMETERS";
    public static final String TEMPLATE_RENDERING_FAILED = "TEMPLATE_RENDERING_FAILED";
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String DUPLICATE_EVENT_ID = "DUPLICATE_EVENT_ID";
}
