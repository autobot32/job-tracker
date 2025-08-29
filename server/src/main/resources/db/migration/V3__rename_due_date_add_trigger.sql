-- 1) Rename applications.due_date -> next_due_at and convert to TIMESTAMPTZ.
--    Existing DATE values (if any) become midnight UTC of that date.
ALTER TABLE applications
    RENAME COLUMN due_date TO next_due_at;

ALTER TABLE applications
ALTER COLUMN next_due_at TYPE TIMESTAMPTZ
  USING CASE
         WHEN next_due_at IS NULL THEN NULL
         ELSE (next_due_at::timestamp AT TIME ZONE 'UTC')
END;

-- 2) Replace the old index name with a clearer one.
DROP INDEX IF EXISTS idx_app_user_due;
CREATE INDEX IF NOT EXISTS idx_app_user_next_due
    ON applications(user_id, next_due_at);

-- 3) Function to recompute next_due_at for a single application
CREATE OR REPLACE FUNCTION compute_next_due_at(p_application_id UUID)
RETURNS VOID AS $$
BEGIN
UPDATE applications a
SET next_due_at = sub.min_due
    FROM (
    SELECT MIN(t.due_at) AS min_due
    FROM tasks t
    WHERE t.application_id = p_application_id
      AND (t.status IS NULL OR t.status NOT IN ('done','cancelled'))  -- adjust to your statuses
  ) sub
WHERE a.id = p_application_id;
END;
$$ LANGUAGE plpgsql;

-- 4) Trigger: keep applications.next_due_at in sync on any task change
CREATE OR REPLACE FUNCTION trg_tasks_recompute_next_due()
RETURNS TRIGGER AS $$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    PERFORM compute_next_due_at(OLD.application_id);
ELSE
    PERFORM compute_next_due_at(NEW.application_id);
END IF;
RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tasks_recompute_next_due ON tasks;
CREATE TRIGGER tasks_recompute_next_due
    AFTER INSERT OR UPDATE OR DELETE ON tasks
    FOR EACH ROW EXECUTE FUNCTION trg_tasks_recompute_next_due();

-- 5) One-time backfill for existing rows
UPDATE applications a
SET next_due_at = sub.min_due
    FROM (
  SELECT application_id, MIN(due_at) AS min_due
  FROM tasks
  WHERE (status IS NULL OR status NOT IN ('done','cancelled'))
  GROUP BY application_id
) sub
WHERE a.id = sub.application_id;