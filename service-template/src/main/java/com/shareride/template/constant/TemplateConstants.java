package com.shareride.template.constant;

import java.time.Duration;

/** Technical constants, Redis locks, endpoints, and RabbitMQ topology for Template Service. */
public final class TemplateConstants {

    private TemplateConstants() {
        // Prevent instantiation
    }

    public static final String PATH_VARIABLE_ID = "id";

    public static final class Endpoint {
        private Endpoint() {}

        public static final String BASE_PATH = "/api/v1/templates";
        public static final String BY_ID = "/{" + PATH_VARIABLE_ID + "}";
    }

    public static final class RedisLock {
        private RedisLock() {}

        public static final String TEMPLATE_ITEM_LOCK_PREFIX = "lock:template:item:";
        public static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(5);
    }

    public static final class RabbitMq {
        private RabbitMq() {}

        public static final String SERVICE_NAME = "template-service";

        // Exchanges
        public static final String EXCHANGE_EVENTS = "shareride.template.events";
        public static final String EXCHANGE_DLX = "shareride.template.dlx";

        // Routing Keys
        public static final String ROUTING_KEY_ITEM_CREATED = "template.item.created";
        public static final String ROUTING_KEY_ITEM_UPDATED = "template.item.updated";
        public static final String ROUTING_KEY_ITEM_DELETED = "template.item.deleted";
        public static final String ROUTING_KEY_DLQ = "template.events.dlq";

        // Queues
        public static final String QUEUE_TEMPLATE_EVENTS = "shareride.template.events.queue";
        public static final String QUEUE_TEMPLATE_EVENTS_DLQ = "shareride.template.events.dlq";
    }

    public static final class Grpc {
        private Grpc() {}

        public static final String AUTH_SERVICE_NAME = "auth-service";
        public static final String TEMPLATE_SERVICE_NAME = "template-service";
    }
}
