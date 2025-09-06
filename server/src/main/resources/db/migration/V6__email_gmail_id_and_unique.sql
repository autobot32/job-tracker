-- Add a stable gmail_id and make it unique
ALTER TABLE emails
    ADD COLUMN IF NOT EXISTS gmail_id TEXT;

-- make sure existing rows are filled before setting NOT NULL (if any)
UPDATE emails SET gmail_id = gmail_id WHERE gmail_id IS NOT NULL;

ALTER TABLE emails
    ALTER COLUMN gmail_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_emails_gmail_id
    ON emails(gmail_id);
