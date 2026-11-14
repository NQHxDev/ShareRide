package com.shareride.notification.constant;

public final class NotificationConstants {

    private NotificationConstants() {}

    public static final String EXCHANGE_NAME = "shareride.notification.exchange";
    public static final String QUEUE_NAME = "shareride.notification.queue";
    public static final String ROUTING_KEY = "notification.email.#";
    public static final String DLX_NAME = "shareride.notification.dlx";
    public static final String DLQ_NAME = "shareride.notification.dlq";

    public static final String DEFAULT_LOCALE = "vi";
    public static final String FALLBACK_LOCALE = "vi";

    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long INITIAL_RETRY_DELAY_MS = 1000L;
    public static final double RETRY_MULTIPLIER = 2.0;

    public static final String MASKED_VALUE = "***";

    public static final String RESEND_API_ENDPOINT = "/emails";
}
