-- Table: users
CREATE TABLE IF NOT EXISTS users (
   id UUID PRIMARY KEY,
   version BIGINT NOT NULL DEFAULT 0,
   email VARCHAR(128) NOT NULL,
   password_hash VARCHAR(255) NOT NULL,
   full_name VARCHAR(128) NOT NULL,
   phone_number VARCHAR(32) NOT NULL,
   role VARCHAR(32) NOT NULL,
   status VARCHAR(32) NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE NOT NULL,
   updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
   created_by VARCHAR(64),
   updated_by VARCHAR(64),
   deleted BOOLEAN NOT NULL DEFAULT FALSE,
   deleted_at TIMESTAMP WITH TIME ZONE,
   deleted_by VARCHAR(64),
   CONSTRAINT uq_users_email UNIQUE (email),
   CONSTRAINT uq_users_phone UNIQUE (phone_number)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_role_status ON users(role, status);

-- Table: refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
   id UUID PRIMARY KEY,
   version BIGINT NOT NULL DEFAULT 0,
   user_id UUID NOT NULL,
   token_hash VARCHAR(255) NOT NULL,
   expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
   revoked BOOLEAN NOT NULL DEFAULT FALSE,
   created_at TIMESTAMP WITH TIME ZONE NOT NULL,
   updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
   created_by VARCHAR(64),
   updated_by VARCHAR(64),
   CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
   CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- Table: outbox_messages (Transactional Outbox Pattern for Auth Service)
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
