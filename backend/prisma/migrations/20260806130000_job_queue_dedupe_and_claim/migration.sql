-- Make the job queue safe for an always-on dispatcher.
--
-- Until now nothing consumed "job_queue": pg_cron enqueued rows and the only
-- consumer was POST /internal/jobs/dispatch behind adminRequired, which nothing
-- called. Three problems have to be fixed before a dispatcher can run
-- unattended.
--
-- 1. `enqueue_job` inserted unconditionally, and
--    'maimaid-catalog-sync-request' fires every 30 minutes, so pending rows
--    accumulate at 48/day forever. A dispatcher would then run catalog_sync
--    once per backlogged row, each one re-fetching the upstream catalog.
--    `enqueue_job` now skips when the same job type is already pending or
--    running, matching the guard `enqueue_static_bundle_build_if_due` already
--    had.
--
-- 2. The accumulated backlog is collapsed to a single pending row per job type
--    so enabling the dispatcher does not immediately replay months of history.
--
-- 3. Claiming needs an index that matches how the dispatcher selects work
--    ("jobType" is part of the claim predicate, and rows are taken in
--    "scheduledAt" order).

create or replace function public.enqueue_job(p_job_type text, p_payload jsonb default '{}'::jsonb)
returns bigint
language plpgsql
as $$
declare
    v_job_id bigint;
begin
    -- Collapse duplicate requests: an unstarted job of this type already covers
    -- the work. Returns the existing id so callers can still report a job.
    select "id"
      into v_job_id
      from "job_queue"
     where "jobType" = p_job_type
       and "status" in ('pending', 'running')
     order by "scheduledAt" asc
     limit 1;

    if found then
        return v_job_id;
    end if;

    insert into "job_queue" ("jobType", "payload", "status", "scheduledAt")
    values (p_job_type, coalesce(p_payload, '{}'::jsonb), 'pending', now())
    returning "id" into v_job_id;

    return v_job_id;
end;
$$;

-- Collapse the existing backlog: keep the oldest pending row per job type so
-- the work still happens once, and drop the rest.
delete from "job_queue"
 where "status" = 'pending'
   and "id" not in (
       select distinct on ("jobType") "id"
         from "job_queue"
        where "status" = 'pending'
        order by "jobType", "scheduledAt" asc
   );

-- Any row left in 'running' is a crashed dispatch from before this migration;
-- nothing was consuming the queue, so these can only be stale.
update "job_queue"
   set "status" = 'failed',
       "finishedAt" = now(),
       "error" = coalesce("error", 'abandoned: no dispatcher was running')
 where "status" = 'running';

create index if not exists "job_queue_jobType_status_idx"
    on "job_queue" ("jobType", "status");
