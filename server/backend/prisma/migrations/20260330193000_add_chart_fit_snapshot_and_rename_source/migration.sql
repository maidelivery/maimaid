DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM static_sources WHERE category = 'df_chart_fit') THEN
    IF EXISTS (SELECT 1 FROM static_sources WHERE category = 'chart_fit') THEN
      DELETE FROM static_sources WHERE category = 'df_chart_fit';
    ELSE
      UPDATE static_sources
      SET category = 'chart_fit'
      WHERE category = 'df_chart_fit';
    END IF;
  END IF;
END;
$$;

CREATE TABLE IF NOT EXISTS chart_fit_snapshots (
  id BIGSERIAL PRIMARY KEY,
  payload_json JSONB NOT NULL,
  meta_json JSONB,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chart_fit_snapshots_created_at
  ON chart_fit_snapshots (created_at);
