ALTER TABLE applications
    ADD COLUMN is_application BOOLEAN DEFAULT TRUE NOT NULL,
ADD COLUMN normalized_company TEXT,
ADD COLUMN normalized_role_title TEXT;

UPDATE applications
SET normalized_company = btrim(regexp_replace(replace(replace(lower(company), ',', ''), '.', ''), '\s+', ' ', 'g')),
    normalized_role_title = btrim(regexp_replace(replace(replace(lower(role_title), ',', ''), '.', ''), '\s+', ' ', 'g'))
WHERE company IS NOT NULL AND role_title IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_user_company_role
    ON applications (user_id, normalized_company, normalized_role_title);
