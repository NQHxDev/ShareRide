-- Make phone_number optional in accounts table
-- This allows users to register with just email and password

-- Remove NOT NULL constraint from phone_number
ALTER TABLE accounts ALTER COLUMN phone_number DROP NOT NULL;

-- Drop unique constraint on phone_number (we'll handle uniqueness in application logic for non-null values)
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS uq_accounts_phone;

-- Add a conditional unique constraint for non-null phone numbers
-- PostgreSQL allows this via partial unique index
DROP INDEX IF EXISTS idx_accounts_phone;
CREATE UNIQUE INDEX idx_accounts_phone_unique ON accounts(phone_number) WHERE phone_number IS NOT NULL;
