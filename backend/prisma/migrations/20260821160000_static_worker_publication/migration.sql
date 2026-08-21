ALTER TABLE "static_bundles"
  DROP COLUMN IF EXISTS "objectKey",
  DROP COLUMN IF EXISTS "payloadJson",
  ADD COLUMN IF NOT EXISTS "manifestUrl" TEXT,
  ADD COLUMN IF NOT EXISTS "bundleUrl" TEXT;

DELETE FROM "job_queue" WHERE "jobType" = 'static_bundle_build';

DO $$
DECLARE
  job_id bigint;
BEGIN
  IF to_regclass('cron.job') IS NULL THEN
    RETURN;
  END IF;

  FOR job_id IN EXECUTE
    'SELECT jobid FROM cron.job WHERE jobname = $1'
    USING 'maimaid-static-bundle-build-request'
  LOOP
    PERFORM cron.unschedule(job_id);
  END LOOP;
EXCEPTION
  WHEN insufficient_privilege OR undefined_function THEN NULL;
END
$$;

DROP TABLE IF EXISTS "static_bundle_schedule_config";
DROP TABLE IF EXISTS "chart_fit_snapshots";
DROP FUNCTION IF EXISTS public.enqueue_static_bundle_build_if_due();
DROP FUNCTION IF EXISTS public.sync_maimaid_static_bundle_cron(boolean, text);
