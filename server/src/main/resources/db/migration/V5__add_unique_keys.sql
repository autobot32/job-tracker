-- Add unique constraints and canonical key for deduplication

-- Ensure emails table has a message_id column
ALTER TABLE emails ADD COLUMN message_id TEXT;

-- Ensure emails table has unique message_id
ALTER TABLE emails ADD CONSTRAINT uq_emails_message_id UNIQUE (message_id);

-- Add canonical_key column to applications table
ALTER TABLE applications ADD COLUMN canonical_key TEXT;

-- Create unique index on canonical_key
CREATE UNIQUE INDEX uq_applications_canonical_key ON applications(canonical_key);
