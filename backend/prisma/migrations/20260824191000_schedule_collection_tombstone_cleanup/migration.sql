DO $$
BEGIN
  IF to_regclass('cron.job') IS NULL THEN
    RETURN;
  END IF;

  PERFORM cron.schedule(
    'maimaid-song-collection-tombstone-cleanup',
    '17 3 * * *',
    $command$SELECT public.enqueue_job('song_collection_tombstone_cleanup', '{}'::jsonb);$command$
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
  WHEN insufficient_privilege OR undefined_function THEN NULL;
END
$$;
