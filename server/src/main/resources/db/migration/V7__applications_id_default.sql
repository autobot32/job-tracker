CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE applications
    ALTER COLUMN id SET DEFAULT gen_random_uuid();