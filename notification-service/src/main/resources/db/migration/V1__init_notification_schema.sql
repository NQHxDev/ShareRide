-- PostgreSQL 17 Flyway Migration for Notification Service
-- Created: 2026-11-14
-- Feature: Notification Service with Resend Email Delivery, Modular Templates, and i18n

-- Table: notification_logs
-- Purpose: Persistent audit and lifecycle tracking for all notification dispatch attempts
CREATE TABLE IF NOT EXISTS notification_logs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_message_id VARCHAR(100),
    retry_count INT NOT NULL DEFAULT 0,
    last_error_message TEXT,
    masked_context_data TEXT,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0
);

-- Unique index for event idempotency (prevents duplicate email dispatch)
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_logs_event_id ON notification_logs(event_id);

-- Lookup indexes for recipient and status queries
CREATE INDEX IF NOT EXISTS idx_notification_logs_recipient ON notification_logs(recipient_email);
CREATE INDEX IF NOT EXISTS idx_notification_logs_status ON notification_logs(status);
CREATE INDEX IF NOT EXISTS idx_notification_logs_created_at ON notification_logs(created_at);

-- Table: outbox_messages
-- Purpose: Transactional Outbox pattern for reliable event publishing
-- Schema aligned with com.shareride.common.outbox.OutboxMessage
CREATE TABLE IF NOT EXISTS outbox_messages (
    outbox_message_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    trace_id VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_messages(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_messages(aggregate_type, aggregate_id);
