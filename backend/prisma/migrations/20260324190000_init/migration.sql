create extension if not exists pgcrypto;
create extension if not exists pg_trgm;
create extension if not exists fuzzystrmatch;
create extension if not exists pg_cron;

create type "UserStatus" as enum ('active', 'disabled');
create type "GameServer" as enum ('jp', 'intl', 'usa', 'cn');
create type "BindingProvider" as enum ('df', 'lxns');
create type "SnapshotStatus" as enum ('pending', 'applied', 'failed');
create type "AliasStatus" as enum ('approved', 'imported', 'proposed');
create type "CandidateStatus" as enum ('voting', 'approved', 'rejected');
create type "ImportProvider" as enum ('df', 'lxns', 'manual');
create type "ImportStatus" as enum ('pending', 'success', 'failed');

create table if not exists "users" (
    "id" uuid primary key default gen_random_uuid(),
    "email" text not null unique,
    "passwordHash" text not null,
    "status" "UserStatus" not null default 'active',
    "isAdmin" boolean not null default false,
    "emailVerifiedAt" timestamptz,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create table if not exists "refresh_tokens" (
    "id" uuid primary key default gen_random_uuid(),
    "userId" uuid not null references "users"("id") on delete cascade,
    "tokenHash" text not null unique,
    "expiresAt" timestamptz not null,
    "revokedAt" timestamptz,
    "userAgent" text,
    "ipAddress" text,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create index if not exists "refresh_tokens_userId_idx" on "refresh_tokens"("userId");
create index if not exists "refresh_tokens_expiresAt_idx" on "refresh_tokens"("expiresAt");

create table if not exists "email_verification_tokens" (
    "id" uuid primary key default gen_random_uuid(),
    "userId" uuid not null references "users"("id") on delete cascade,
    "tokenHash" text not null unique,
    "expiresAt" timestamptz not null,
    "consumedAt" timestamptz,
    "createdAt" timestamptz not null default now()
);

create index if not exists "email_verification_tokens_userId_idx" on "email_verification_tokens"("userId");
create index if not exists "email_verification_tokens_expiresAt_idx" on "email_verification_tokens"("expiresAt");

create table if not exists "password_reset_tokens" (
    "id" uuid primary key default gen_random_uuid(),
    "userId" uuid not null references "users"("id") on delete cascade,
    "tokenHash" text not null unique,
    "expiresAt" timestamptz not null,
    "consumedAt" timestamptz,
    "createdAt" timestamptz not null default now()
);

create index if not exists "password_reset_tokens_userId_idx" on "password_reset_tokens"("userId");
create index if not exists "password_reset_tokens_expiresAt_idx" on "password_reset_tokens"("expiresAt");

create table if not exists "profiles" (
    "id" uuid primary key default gen_random_uuid(),
    "userId" uuid not null references "users"("id") on delete cascade,
    "name" text not null,
    "server" "GameServer" not null default 'jp',
    "avatarUrl" text,
    "avatarObjectKey" text,
    "isActive" boolean not null default false,
    "dfUsername" text not null default '',
    "dfImportToken" text not null default '',
    "lxnsRefreshToken" text not null default '',
    "lxnsClientId" text not null default 'cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338',
    "playerRating" integer not null default 0,
    "plate" text,
    "lastImportDateDf" timestamptz,
    "lastImportDateLxns" timestamptz,
    "b35Count" integer not null default 35,
    "b15Count" integer not null default 15,
    "b35RecLimit" integer not null default 10,
    "b15RecLimit" integer not null default 10,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create index if not exists "profiles_userId_idx" on "profiles"("userId");
create index if not exists "profiles_userId_isActive_idx" on "profiles"("userId", "isActive");
create index if not exists "profiles_dfImportToken_idx" on "profiles"("dfImportToken");

create table if not exists "profile_bindings" (
    "id" uuid primary key default gen_random_uuid(),
    "profileId" uuid not null references "profiles"("id") on delete cascade,
    "provider" "BindingProvider" not null,
    "externalUserId" text,
    "externalUsername" text,
    "credentialJson" jsonb,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("profileId", "provider")
);

create index if not exists "profile_bindings_provider_externalUserId_idx" on "profile_bindings"("provider", "externalUserId");

create table if not exists "catalog_snapshots" (
    "id" bigserial primary key,
    "source" text not null,
    "sourceUrl" text not null,
    "etag" text,
    "payloadHash" text not null,
    "status" "SnapshotStatus" not null default 'pending',
    "metadataJson" jsonb,
    "payloadJson" jsonb not null,
    "fetchedAt" timestamptz not null default now(),
    "activatedAt" timestamptz,
    unique ("source", "payloadHash")
);

create index if not exists "catalog_snapshots_source_status_fetchedAt_idx" on "catalog_snapshots"("source", "status", "fetchedAt");

create table if not exists "songs" (
    "songIdentifier" text primary key,
    "songId" integer not null default 0,
    "category" text not null,
    "title" text not null,
    "artist" text not null,
    "imageName" text not null,
    "version" text,
    "releaseDate" timestamptz,
    "sortOrder" integer not null default 0,
    "bpm" double precision,
    "isNew" boolean not null default false,
    "isLocked" boolean not null default false,
    "comment" text,
    "searchKeywords" text,
    "disabled" boolean not null default false,
    "snapshotId" bigint references "catalog_snapshots"("id") on delete set null,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create index if not exists "songs_snapshotId_idx" on "songs"("snapshotId");
create index if not exists "songs_songId_idx" on "songs"("songId");
create index if not exists "songs_title_idx" on "songs"("title");

create table if not exists "sheets" (
    "id" bigserial primary key,
    "songIdentifier" text not null references "songs"("songIdentifier") on delete cascade,
    "songId" integer not null default 0,
    "chartType" text not null,
    "difficulty" text not null,
    "version" text,
    "level" text not null,
    "levelValue" numeric(5,2),
    "internalLevel" text,
    "internalLevelValue" numeric(5,2),
    "noteDesigner" text,
    "tap" integer,
    "hold" integer,
    "slide" integer,
    "touch" integer,
    "breakCount" integer,
    "total" integer,
    "regionJp" boolean not null default true,
    "regionIntl" boolean not null default false,
    "regionUsa" boolean not null default false,
    "regionCn" boolean not null default false,
    "isSpecial" boolean not null default false,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("songIdentifier", "chartType", "difficulty")
);

create index if not exists "sheets_songId_idx" on "sheets"("songId");
create index if not exists "sheets_chartType_difficulty_idx" on "sheets"("chartType", "difficulty");

create table if not exists "aliases" (
    "id" bigserial primary key,
    "songIdentifier" text not null references "songs"("songIdentifier") on delete cascade,
    "aliasText" text not null,
    "aliasNorm" text not null,
    "source" text not null,
    "status" "AliasStatus" not null default 'approved',
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("songIdentifier", "aliasNorm", "source")
);

create index if not exists "aliases_songIdentifier_status_idx" on "aliases"("songIdentifier", "status");

create table if not exists "icons" (
    "id" integer primary key,
    "name" text not null,
    "descriptionText" text not null,
    "genre" text not null,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create table if not exists "best_scores" (
    "id" uuid primary key default gen_random_uuid(),
    "profileId" uuid not null references "profiles"("id") on delete cascade,
    "sheetId" bigint not null references "sheets"("id") on delete cascade,
    "achievements" numeric(6,4) not null,
    "rank" text not null,
    "dxScore" integer not null default 0,
    "fc" text,
    "fs" text,
    "achievedAt" timestamptz not null,
    "source" text not null,
    "sourcePayload" jsonb,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("profileId", "sheetId")
);

create index if not exists "best_scores_profileId_updatedAt_idx" on "best_scores"("profileId", "updatedAt");

create table if not exists "play_records" (
    "id" uuid primary key default gen_random_uuid(),
    "profileId" uuid not null references "profiles"("id") on delete cascade,
    "sheetId" bigint not null references "sheets"("id") on delete cascade,
    "achievements" numeric(6,4) not null,
    "rank" text not null,
    "dxScore" integer not null default 0,
    "fc" text,
    "fs" text,
    "playTime" timestamptz not null,
    "source" text not null,
    "sourcePayload" jsonb,
    "createdAt" timestamptz not null default now()
);

create index if not exists "play_records_profileId_playTime_idx" on "play_records"("profileId", "playTime");

create table if not exists "import_runs" (
    "id" uuid primary key default gen_random_uuid(),
    "profileId" uuid not null references "profiles"("id") on delete cascade,
    "provider" "ImportProvider" not null,
    "status" "ImportStatus" not null default 'pending',
    "startedAt" timestamptz not null default now(),
    "finishedAt" timestamptz,
    "summaryJson" jsonb,
    "errorMessage" text
);

create index if not exists "import_runs_profileId_startedAt_idx" on "import_runs"("profileId", "startedAt");

create table if not exists "import_raw_payloads" (
    "id" uuid primary key default gen_random_uuid(),
    "importRunId" uuid not null references "import_runs"("id") on delete cascade,
    "payloadType" text not null,
    "payloadJson" jsonb not null,
    "createdAt" timestamptz not null default now()
);

create index if not exists "import_raw_payloads_importRunId_idx" on "import_raw_payloads"("importRunId");

create table if not exists "community_alias_candidates" (
    "id" uuid primary key default gen_random_uuid(),
    "songIdentifier" text not null,
    "aliasText" text not null,
    "aliasNorm" text not null,
    "submitterId" uuid not null references "users"("id") on delete cascade,
    "status" "CandidateStatus" not null default 'voting',
    "voteOpenAt" timestamptz,
    "voteCloseAt" timestamptz,
    "approvedAt" timestamptz,
    "rejectedAt" timestamptz,
    "submittedLocalDate" date not null,
    "submittedTzOffsetMin" integer not null,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("songIdentifier", "aliasNorm", "status")
);

create index if not exists "community_alias_candidates_songIdentifier_status_idx" on "community_alias_candidates"("songIdentifier", "status");
create index if not exists "community_alias_candidates_voteCloseAt_idx" on "community_alias_candidates"("voteCloseAt");
create index if not exists "community_alias_candidates_aliasNorm_trgm_idx" on "community_alias_candidates" using gin ("aliasNorm" gin_trgm_ops);

create table if not exists "community_alias_votes" (
    "id" uuid primary key default gen_random_uuid(),
    "candidateId" uuid not null references "community_alias_candidates"("id") on delete cascade,
    "voterId" uuid not null references "users"("id") on delete cascade,
    "vote" integer not null check ("vote" in (-1, 1)),
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("candidateId", "voterId")
);

create index if not exists "community_alias_votes_candidateId_idx" on "community_alias_votes"("candidateId");
create index if not exists "community_alias_votes_voterId_idx" on "community_alias_votes"("voterId");

create table if not exists "job_queue" (
    "id" bigserial primary key,
    "jobType" text not null,
    "payload" jsonb not null default '{}'::jsonb,
    "status" text not null default 'pending',
    "scheduledAt" timestamptz not null default now(),
    "startedAt" timestamptz,
    "finishedAt" timestamptz,
    "error" text,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create index if not exists "job_queue_status_scheduledAt_idx" on "job_queue"("status", "scheduledAt");

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new."updatedAt" = now();
    return new;
end;
$$;

drop trigger if exists trg_users_touch on "users";
create trigger trg_users_touch before update on "users" for each row execute function public.touch_updated_at();
drop trigger if exists trg_refresh_tokens_touch on "refresh_tokens";
create trigger trg_refresh_tokens_touch before update on "refresh_tokens" for each row execute function public.touch_updated_at();
drop trigger if exists trg_profiles_touch on "profiles";
create trigger trg_profiles_touch before update on "profiles" for each row execute function public.touch_updated_at();
drop trigger if exists trg_profile_bindings_touch on "profile_bindings";
create trigger trg_profile_bindings_touch before update on "profile_bindings" for each row execute function public.touch_updated_at();
drop trigger if exists trg_songs_touch on "songs";
create trigger trg_songs_touch before update on "songs" for each row execute function public.touch_updated_at();
drop trigger if exists trg_sheets_touch on "sheets";
create trigger trg_sheets_touch before update on "sheets" for each row execute function public.touch_updated_at();
drop trigger if exists trg_aliases_touch on "aliases";
create trigger trg_aliases_touch before update on "aliases" for each row execute function public.touch_updated_at();
drop trigger if exists trg_icons_touch on "icons";
create trigger trg_icons_touch before update on "icons" for each row execute function public.touch_updated_at();
drop trigger if exists trg_best_scores_touch on "best_scores";
create trigger trg_best_scores_touch before update on "best_scores" for each row execute function public.touch_updated_at();
drop trigger if exists trg_community_alias_candidates_touch on "community_alias_candidates";
create trigger trg_community_alias_candidates_touch before update on "community_alias_candidates" for each row execute function public.touch_updated_at();
drop trigger if exists trg_community_alias_votes_touch on "community_alias_votes";
create trigger trg_community_alias_votes_touch before update on "community_alias_votes" for each row execute function public.touch_updated_at();
drop trigger if exists trg_job_queue_touch on "job_queue";
create trigger trg_job_queue_touch before update on "job_queue" for each row execute function public.touch_updated_at();

create or replace function public.community_alias_normalize(raw text)
returns text
language plpgsql
immutable
as $$
declare
    s text;
begin
    s := coalesce(raw, '');
    s := trim(s);
    s := translate(
        s,
        '０１２３４５６７８９ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ　',
        '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz '
    );
    s := lower(s);
    s := regexp_replace(s, '[[:space:]]+', '', 'g');
    s := regexp_replace(s, '[[:punct:]]+', '', 'g');
    s := regexp_replace(s, '[，。！？、；：·・•（）【】《》〈〉「」『』“”‘’—～＿－…￥]+', '', 'g');
    return s;
end;
$$;

create or replace function public.community_alias_cycle_start(p_ts timestamptz default now())
returns timestamptz
language plpgsql
stable
as $$
declare
    local_ts timestamp;
    local_date date;
    day_offset integer;
    cycle_start_local timestamp;
begin
    local_ts := p_ts at time zone 'Asia/Shanghai';
    local_date := date_trunc('day', local_ts)::date;
    day_offset := ((local_date - date '1970-01-01')::integer % 3);
    cycle_start_local := (local_date - day_offset)::timestamp;
    return cycle_start_local at time zone 'Asia/Shanghai';
end;
$$;

create or replace function public.community_alias_cycle_end(p_ts timestamptz default now())
returns timestamptz
language sql
stable
as $$
select public.community_alias_cycle_start(p_ts) + interval '3 days' - interval '1 second';
$$;

create or replace function public.community_alias_roll_cycle(p_now timestamptz default now())
returns jsonb
language plpgsql
as $$
declare
    v_now timestamptz := coalesce(p_now, now());
    v_settled_count integer := 0;
begin
    with due as (
        select
            c."id",
            coalesce(sum(case when v."vote" = 1 then 1 else 0 end), 0)::integer as support_count,
            coalesce(sum(case when v."vote" = -1 then 1 else 0 end), 0)::integer as oppose_count
        from "community_alias_candidates" c
        left join "community_alias_votes" v on v."candidateId" = c."id"
        where c."status" = 'voting'
          and c."voteCloseAt" is not null
          and c."voteCloseAt" <= v_now
        group by c."id"
    ),
    settled as (
        update "community_alias_candidates" c
           set "status" = case when d.support_count > d.oppose_count and d.support_count >= 3 then 'approved' else 'rejected' end::"CandidateStatus",
               "approvedAt" = case when d.support_count > d.oppose_count and d.support_count >= 3 then v_now else null end,
               "rejectedAt" = case when d.support_count > d.oppose_count and d.support_count >= 3 then null else v_now end,
               "updatedAt" = v_now
          from due d
         where c."id" = d."id"
         returning c."id"
    )
    select count(*)::integer into v_settled_count from settled;

    insert into "aliases" ("songIdentifier", "aliasText", "aliasNorm", "source", "status")
    select
        c."songIdentifier",
        c."aliasText",
        c."aliasNorm",
        'community',
        'approved'::"AliasStatus"
    from "community_alias_candidates" c
    where c."status" = 'approved'
    on conflict ("songIdentifier", "aliasNorm", "source") do update
    set "aliasText" = excluded."aliasText",
        "updatedAt" = now();

    return jsonb_build_object('now', v_now, 'settled_count', v_settled_count);
end;
$$;

create or replace function public.enqueue_job(p_job_type text, p_payload jsonb default '{}'::jsonb)
returns bigint
language plpgsql
as $$
declare
    v_job_id bigint;
begin
    insert into "job_queue" ("jobType", "payload", "status", "scheduledAt")
    values (p_job_type, coalesce(p_payload, '{}'::jsonb), 'pending', now())
    returning "id" into v_job_id;

    return v_job_id;
end;
$$;

do $$
begin
    if exists(select 1 from cron.job where jobname = 'maimaid-community-alias-roll') then
        perform cron.unschedule((select jobid from cron.job where jobname = 'maimaid-community-alias-roll' limit 1));
    end if;
    if exists(select 1 from cron.job where jobname = 'maimaid-catalog-sync-request') then
        perform cron.unschedule((select jobid from cron.job where jobname = 'maimaid-catalog-sync-request' limit 1));
    end if;
exception
    when undefined_table then
        null;
end;
$$;

select cron.schedule(
    'maimaid-community-alias-roll',
    '* * * * *',
    $$select public.community_alias_roll_cycle();$$
);

select cron.schedule(
    'maimaid-catalog-sync-request',
    '*/30 * * * *',
    $$select public.enqueue_job('catalog_sync', '{}'::jsonb);$$
);
