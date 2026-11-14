package com.shareride.notification.exception;

/**
 * Exception thrown when email delivery fails with an unrecoverable error that should trigger
 * RabbitMQ DLQ routing.
 *
 * <p>Examples: permanent 4xx errors (invalid email), exhausted retry attempts on transient errors.
 */
public class UnrecoverableDeliveryException extends RuntimeException {

    public UnrecoverableDeliveryException(String message) {
        super(message);
    }

    public UnrecoverableDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
