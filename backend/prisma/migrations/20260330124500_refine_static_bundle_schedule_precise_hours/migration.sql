alter table "static_bundle_schedule_config"
  drop constraint if exists "static_bundle_schedule_config_intervalHours_check";

alter table "static_bundle_schedule_config"
  add column if not exists "lastEnqueuedAt" timestamptz,
  add column if not exists "nextEnqueueAt" timestamptz;

alter table "static_bundle_schedule_config"
  add constraint "static_bundle_schedule_config_intervalHours_check"
  check ("intervalHours" >= 1);

update "static_bundle_schedule_config"
set "nextEnqueueAt" = coalesce(
  "nextEnqueueAt",
  now() + make_interval(hours => greatest(1, "intervalHours"))
)
where "id" = 1;

create or replace function public.enqueue_static_bundle_build_if_due()
returns jsonb
language plpgsql
as $$
declare
  v_now timestamptz := now();
  v_cfg record;
  v_interval_hours integer;
  v_next_enqueue_at timestamptz;
  v_job_id bigint;
begin
  select *
    into v_cfg
    from "static_bundle_schedule_config"
   where "id" = 1
   for update;

  if not found then
    insert into "static_bundle_schedule_config" ("id", "enabled", "intervalHours", "nextEnqueueAt")
    values (1, true, 6, v_now + interval '6 hours')
    returning * into v_cfg;
  end if;

  if not coalesce(v_cfg."enabled", false) then
    return jsonb_build_object('enqueued', false, 'reason', 'disabled');
  end if;

  v_interval_hours := greatest(1, coalesce(v_cfg."intervalHours", 6));
  v_next_enqueue_at := coalesce(v_cfg."nextEnqueueAt", v_now + make_interval(hours => v_interval_hours));

  if v_cfg."nextEnqueueAt" is null then
    update "static_bundle_schedule_config"
       set "nextEnqueueAt" = v_next_enqueue_at
     where "id" = v_cfg."id";
    return jsonb_build_object('enqueued', false, 'reason', 'next_initialized', 'nextEnqueueAt', v_next_enqueue_at);
  end if;

  if v_now < v_next_enqueue_at then
    return jsonb_build_object('enqueued', false, 'reason', 'not_due', 'nextEnqueueAt', v_next_enqueue_at);
  end if;

  if exists (
    select 1
      from "job_queue"
     where "jobType" = 'static_bundle_build'
       and "status" in ('pending', 'running')
  ) then
    update "static_bundle_schedule_config"
       set "nextEnqueueAt" = v_now + make_interval(hours => v_interval_hours)
     where "id" = v_cfg."id";
    return jsonb_build_object('enqueued', false, 'reason', 'already_queued', 'nextEnqueueAt', v_now + make_interval(hours => v_interval_hours));
  end if;

  insert into "job_queue" ("jobType", "payload", "status", "scheduledAt")
  values ('static_bundle_build', '{}'::jsonb, 'pending', v_now)
  returning "id" into v_job_id;

  update "static_bundle_schedule_config"
     set "lastEnqueuedAt" = v_now,
         "nextEnqueueAt" = v_now + make_interval(hours => v_interval_hours)
   where "id" = v_cfg."id";

  return jsonb_build_object(
    'enqueued', true,
    'jobId', v_job_id,
    'nextEnqueueAt', v_now + make_interval(hours => v_interval_hours)
  );
end;
$$;
