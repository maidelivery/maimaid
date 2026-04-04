create table if not exists "static_bundle_schedule_config" (
  "id" integer primary key default 1,
  "enabled" boolean not null default true,
  "intervalHours" integer not null default 6,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now(),
  constraint "static_bundle_schedule_config_intervalHours_check" check ("intervalHours" between 1 and 24)
);

insert into "static_bundle_schedule_config" ("id", "enabled", "intervalHours")
values (1, true, 6)
on conflict ("id") do nothing;

create or replace function public.static_bundle_schedule_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new."updatedAt" := now();
  return new;
end;
$$;

drop trigger if exists trg_static_bundle_schedule_config_touch on "static_bundle_schedule_config";
create trigger trg_static_bundle_schedule_config_touch
before update on "static_bundle_schedule_config"
for each row
execute function public.static_bundle_schedule_touch_updated_at();
