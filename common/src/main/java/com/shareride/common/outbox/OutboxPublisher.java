package com.shareride.common.outbox;

/**
 * Strategy interface for publishing relayed outbox domain events to downstream message brokers
 * (such as Apache Kafka, RabbitMQ, AWS SQS) or an in-process event bus.
 */
@FunctionalInterface
public interface OutboxPublisher {

    /**
     * Publishes a single outbox message to the transport target.
     *
     * @param message the outbox message entity containing event details and JSON payload
     * @throws Exception if sending fails, which prompts the relay runner to retry or dead-letter
     */
    void publish(OutboxMessage message) throws Exception;
}
