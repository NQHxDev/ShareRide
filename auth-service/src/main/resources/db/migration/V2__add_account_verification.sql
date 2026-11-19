-- Migration: V2__add_account_verification.sql
-- Description: Adds account_verifications table for asynchronous email verification

CREATE TABLE IF NOT EXISTS account_verifications (
    verification_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    account_id UUID NOT NULL,
    verification_code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT fk_account_verifications_account
        FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_account_verifications_account_id
    ON account_verifications(account_id);

CREATE INDEX IF NOT EXISTS idx_account_verifications_account_status
    ON account_verifications(account_id, status);
