-- Table: template_items
CREATE TABLE IF NOT EXISTS template_items (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(64),
    CONSTRAINT uq_template_items_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_template_items_code ON template_items(code);
CREATE INDEX IF NOT EXISTS idx_template_items_status ON template_items(status);
CREATE INDEX IF NOT EXISTS idx_template_items_deleted ON template_items(deleted);

-- Table: outbox_messages (Transactional Outbox Pattern)
CREATE TABLE IF NOT EXISTS outbox_messages (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    trace_id VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_messages(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_messages(aggregate_type, aggregate_id);

