CREATE TABLE daily_ingest_usage (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  usage_date DATE NOT NULL,
  run_count INTEGER NOT NULL DEFAULT 0,
  llm_email_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_daily_ingest_usage_user_date UNIQUE (user_id, usage_date),
  CONSTRAINT chk_daily_ingest_usage_run_count_nonnegative CHECK (run_count >= 0),
  CONSTRAINT chk_daily_ingest_usage_llm_email_count_nonnegative CHECK (llm_email_count >= 0)
);

CREATE INDEX idx_daily_ingest_usage_user_date
  ON daily_ingest_usage (user_id, usage_date);
