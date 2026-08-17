CREATE OR REPLACE FUNCTION public.sync_maimaid_static_bundle_cron(
  schedule_enabled boolean,
  schedule_expression text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, cron
AS $$
DECLARE
  existing_job_id bigint;
BEGIN
  SELECT jobid
  INTO existing_job_id
  FROM cron.job
  WHERE jobname = 'maimaid-static-bundle-build-request'
  LIMIT 1;

  IF existing_job_id IS NOT NULL THEN
    PERFORM cron.unschedule(existing_job_id);
  END IF;

  IF schedule_enabled THEN
    PERFORM cron.schedule(
      'maimaid-static-bundle-build-request',
      schedule_expression,
      $command$SELECT public.enqueue_static_bundle_build_if_due();$command$
    );
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.sync_maimaid_static_bundle_cron(boolean, text) FROM PUBLIC;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'maimaid_app') THEN
    GRANT EXECUTE ON FUNCTION public.sync_maimaid_static_bundle_cron(boolean, text) TO maimaid_app;
  END IF;
END;
$$;
