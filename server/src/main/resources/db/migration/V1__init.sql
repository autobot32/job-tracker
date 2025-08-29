-- users
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  provider TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- oauth_tokens
CREATE TABLE oauth_tokens (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  provider TEXT NOT NULL,
  access_token TEXT NOT NULL,
  refresh_token TEXT,
  expires_at TIMESTAMPTZ
);

-- emails
CREATE TABLE emails (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  thread_id TEXT NOT NULL,
  message_id_hash TEXT NOT NULL,
  from_addr TEXT,
  to_addr TEXT,
  subject TEXT,
  sent_at TIMESTAMPTZ,
  body_text TEXT,
  raw_label TEXT,
  llm_type TEXT,
  UNIQUE (user_id, message_id_hash)
);

-- applications
CREATE TABLE applications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  company TEXT NOT NULL,
  role_title TEXT NOT NULL,
  location TEXT,
  source_email_id UUID REFERENCES emails(id) ON DELETE SET NULL,
  first_seen_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL,
  next_step TEXT,
  due_date DATE,
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT
);
CREATE INDEX idx_app_user_company_role ON applications(user_id, company, role_title);
CREATE INDEX idx_app_user_status ON applications(user_id, status);
CREATE INDEX idx_app_user_due ON applications(user_id, due_date);

-- tasks
CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  due_at TIMESTAMPTZ,
  status TEXT NOT NULL,
  source TEXT NOT NULL
);

-- contacts
CREATE TABLE contacts (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  name TEXT,
  email TEXT,
  role TEXT
);
