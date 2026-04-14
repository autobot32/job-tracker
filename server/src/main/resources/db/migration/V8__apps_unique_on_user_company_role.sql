-- Remove any uniqueness on canonical_key (not used for dedupe right now)
DROP INDEX IF EXISTS uq_applications_canonical_key;

-- Ensure normalized fields are never null
ALTER TABLE applications
    ALTER COLUMN normalized_company SET NOT NULL,
ALTER COLUMN normalized_role_title SET NOT NULL;

-- Enforce deduplication only on (user_id, normalized_company, normalized_role_title)
CREATE UNIQUE INDEX IF NOT EXISTS uq_apps_user_normco_normrole
    ON applications (user_id, normalized_company, normalized_role_title);
