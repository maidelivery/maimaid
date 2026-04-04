DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_fit_snapshots'
      AND column_name = 'payload_json'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_fit_snapshots'
      AND column_name = 'payloadJson'
  ) THEN
    ALTER TABLE chart_fit_snapshots
      RENAME COLUMN payload_json TO "payloadJson";
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_fit_snapshots'
      AND column_name = 'meta_json'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_fit_snapshots'
      AND column_name = 'metaJson'
  ) THEN
    ALTER TABLE chart_fit_snapshots
      RENAME COLUMN meta_json TO "metaJson";
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_fit_snapshots'
      AND column_name = 'created_at'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_fit_snapshots'
      AND column_name = 'createdAt'
  ) THEN
    ALTER TABLE chart_fit_snapshots
      RENAME COLUMN created_at TO "createdAt";
  END IF;
END;
$$;
