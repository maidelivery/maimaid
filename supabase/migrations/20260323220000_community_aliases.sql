-- Community aliases: submission, voting, and 3-day cycle rollout
-- Timezone anchor: Asia/Shanghai

create extension if not exists pgcrypto;
create extension if not exists pg_trgm;
create extension if not exists fuzzystrmatch;

create table if not exists public.community_alias_candidates (
    id uuid primary key default gen_random_uuid(),
    song_identifier text not null,
INv    alias_text text not null,
    alias_norm text not null,
    submitter_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'voting' check (status in ('voting', 'approved', 'rejected')),
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    submitted_local_date date not null,
    submitted_tz_offset_min integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.community_alias_votes (
    id uuid primary key default gen_random_uuid(),
    candidate_id uuid not null references public.community_alias_candidates(id) on delete cascade,
    voter_id uuid not null references auth.users(id) on delete cascade,
    vote smallint not null check (vote in (-1, 1)),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (candidate_id, voter_id)
);

create index if not exists idx_community_alias_candidates_song_status
    on public.community_alias_candidates(song_identifier, status);

create index if not exists idx_community_alias_candidates_vote_close_at
    on public.community_alias_candidates(vote_close_at);

create index if not exists idx_community_alias_votes_candidate
    on public.community_alias_votes(candidate_id);

create index if not exists idx_community_alias_candidates_alias_norm_trgm
    on public.community_alias_candidates using gin (alias_norm gin_trgm_ops);

create unique index if not exists uq_community_alias_candidates_song_norm_active
    on public.community_alias_candidates(song_identifier, alias_norm)
    where status in ('voting', 'approved');

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_community_alias_candidates_touch on public.community_alias_candidates;
create trigger trg_community_alias_candidates_touch
before update on public.community_alias_candidates
for each row execute function public.touch_updated_at();

drop trigger if exists trg_community_alias_votes_touch on public.community_alias_votes;
create trigger trg_community_alias_votes_touch
before update on public.community_alias_votes
for each row execute function public.touch_updated_at();

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

    -- Width folding for common full-width ASCII ranges.
    s := translate(
        s,
        '０１２３４５６７８９ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ　',
        '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz '
    );

    s := lower(s);
    s := regexp_replace(s, '[[:space:]]+', '', 'g');

    -- Remove both ASCII punctuation and common CJK symbols.
    s := regexp_replace(s, '[[:punct:]]+', '', 'g');
    s := regexp_replace(s, '[，。！？、；：·・•（）【】《》〈〉「」『』“”‘’—～＿－…￥]+', '', 'g');

    return s;
end;
$$;

create or replace function public.community_alias_bucket(
    p_norm_len integer,
    p_similarity real,
    p_levenshtein integer,
    p_exact boolean
)
returns text
language plpgsql
immutable
as $$
begin
    if p_exact then
        return 'exact';
    end if;

    if p_norm_len <= 3 then
        return 'low';
    elsif p_norm_len between 4 and 5 then
        if p_similarity >= 0.92 or p_levenshtein <= 1 then
            return 'high';
        elsif p_similarity >= 0.72 then
            return 'gray';
        else
            return 'low';
        end if;
    elsif p_norm_len between 6 and 8 then
        if p_similarity >= 0.85 or p_levenshtein <= 1 then
            return 'high';
        elsif p_similarity >= 0.72 then
            return 'gray';
        else
            return 'low';
        end if;
    else
        if p_similarity >= 0.80 or p_levenshtein <= 2 then
            return 'high';
        elsif p_similarity >= 0.72 then
            return 'gray';
        else
            return 'low';
        end if;
    end if;
end;
$$;

create or replace function public.community_alias_find_candidates_for_dedupe(
    p_song_identifier text,
    p_alias_text text,
    p_limit integer default 20
)
returns table (
    candidate_id uuid,
    alias_text text,
    status text,
    similarity_score real,
    levenshtein_distance integer,
    bucket text,
    support_count integer,
    oppose_count integer
)
language sql
stable
as $$
with input as (
    select
        public.community_alias_normalize(p_alias_text) as norm,
        length(public.community_alias_normalize(p_alias_text))::integer as norm_len
),
vote_stats as (
    select
        v.candidate_id,
        coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
        coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
    from public.community_alias_votes v
    group by v.candidate_id
),
candidates as (
    select
        c.id as candidate_id,
        c.alias_text,
        c.status,
        similarity(c.alias_norm, i.norm)::real as similarity_score,
        levenshtein(left(c.alias_norm, 64), left(i.norm, 64))::integer as levenshtein_distance,
        public.community_alias_bucket(
            i.norm_len,
            similarity(c.alias_norm, i.norm)::real,
            levenshtein(left(c.alias_norm, 64), left(i.norm, 64))::integer,
            c.alias_norm = i.norm
        ) as bucket,
        coalesce(vs.support_count, 0)::integer as support_count,
        coalesce(vs.oppose_count, 0)::integer as oppose_count
    from public.community_alias_candidates c
    cross join input i
    left join vote_stats vs on vs.candidate_id = c.id
    where c.song_identifier = p_song_identifier
      and c.status in ('voting', 'approved')
)
select *
from candidates
order by
    case bucket
        when 'exact' then 0
        when 'high' then 1
        when 'gray' then 2
        else 3
    end,
    similarity_score desc,
    levenshtein_distance asc,
    alias_text asc
limit greatest(1, least(coalesce(p_limit, 20), 50));
$$;

create or replace function public.community_alias_count_daily_creations(
    p_local_date date
)
returns integer
language sql
stable
as $$
select count(*)::integer
from public.community_alias_candidates c
where c.submitter_id = auth.uid()
  and c.submitted_local_date = p_local_date;
$$;

create or replace function public.community_alias_get_voting_board(
    p_limit integer default 100,
    p_offset integer default 0
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    submitter_id uuid,
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    support_count integer,
    oppose_count integer,
    my_vote smallint,
    created_at timestamptz
)
language sql
stable
as $$
with vote_stats as (
    select
        v.candidate_id,
        coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
        coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
    from public.community_alias_votes v
    group by v.candidate_id
),
my_votes as (
    select candidate_id, vote
    from public.community_alias_votes
    where voter_id = auth.uid()
)
select
    c.id as candidate_id,
    c.song_identifier,
    c.alias_text,
    c.submitter_id,
    c.vote_open_at,
    c.vote_close_at,
    coalesce(vs.support_count, 0)::integer as support_count,
    coalesce(vs.oppose_count, 0)::integer as oppose_count,
    mv.vote as my_vote,
    c.created_at
from public.community_alias_candidates c
left join vote_stats vs on vs.candidate_id = c.id
left join my_votes mv on mv.candidate_id = c.id
where c.status = 'voting'
order by c.vote_close_at asc, c.created_at desc
limit greatest(1, least(coalesce(p_limit, 100), 200))
offset greatest(0, coalesce(p_offset, 0));
$$;

create or replace function public.community_alias_get_my_song_candidates(
    p_song_identifier text,
    p_limit integer default 50
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    status text,
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    support_count integer,
    oppose_count integer,
    created_at timestamptz,
    updated_at timestamptz
)
language sql
stable
as $$
with vote_stats as (
    select
        v.candidate_id,
        coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
        coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
    from public.community_alias_votes v
    group by v.candidate_id
)
select
    c.id as candidate_id,
    c.song_identifier,
    c.alias_text,
    c.status,
    c.vote_open_at,
    c.vote_close_at,
    coalesce(vs.support_count, 0)::integer as support_count,
    coalesce(vs.oppose_count, 0)::integer as oppose_count,
    c.created_at,
    c.updated_at
from public.community_alias_candidates c
left join vote_stats vs on vs.candidate_id = c.id
where c.submitter_id = auth.uid()
  and c.song_identifier = p_song_identifier
order by c.created_at desc
limit greatest(1, least(coalesce(p_limit, 50), 200));
$$;

create or replace function public.community_alias_vote(
    p_candidate_id uuid,
    p_vote smallint
)
returns table (
    candidate_id uuid,
    support_count integer,
    oppose_count integer,
    my_vote smallint
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
    v_status text;
    v_open timestamptz;
    v_close timestamptz;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        raise exception 'Not authenticated';
    end if;

    if p_vote not in (-1, 1) then
        raise exception 'Invalid vote value';
    end if;

    select c.status, c.vote_open_at, c.vote_close_at
      into v_status, v_open, v_close
    from public.community_alias_candidates c
    where c.id = p_candidate_id;

    if v_status is null then
        raise exception 'Candidate not found';
    end if;

    if v_status <> 'voting' then
        raise exception 'Candidate is not in voting status';
    end if;

    if (v_open is not null and now() < v_open) or (v_close is not null and now() > v_close) then
        raise exception 'Voting window is closed';
    end if;

    insert into public.community_alias_votes(candidate_id, voter_id, vote)
    values (p_candidate_id, v_user_id, p_vote)
    on conflict (candidate_id, voter_id)
    do update set vote = excluded.vote, updated_at = now();

    return query
    with vote_stats as (
        select
            v.candidate_id,
            coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
            coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
        from public.community_alias_votes v
        where v.candidate_id = p_candidate_id
        group by v.candidate_id
    )
    select
        p_candidate_id,
        coalesce(vs.support_count, 0)::integer,
        coalesce(vs.oppose_count, 0)::integer,
        p_vote
    from vote_stats vs;
end;
$$;

create or replace function public.community_alias_sync_approved_since(
    p_since timestamptz default null,
    p_limit integer default 500
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    updated_at timestamptz,
    approved_at timestamptz
)
language sql
stable
as $$
select
    c.id as candidate_id,
    c.song_identifier,
    c.alias_text,
    c.updated_at,
    c.approved_at
from public.community_alias_candidates c
where c.status = 'approved'
  and (p_since is null or c.updated_at > p_since)
order by c.updated_at asc
limit greatest(1, least(coalesce(p_limit, 500), 2000));
$$;

create or replace function public.community_alias_cycle_start(
    p_ts timestamptz default now()
)
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

create or replace function public.community_alias_cycle_end(
    p_ts timestamptz default now()
)
returns timestamptz
language sql
stable
as $$
select public.community_alias_cycle_start(p_ts) + interval '3 days' - interval '1 second';
$$;

create or replace function public.community_alias_roll_cycle(
    p_now timestamptz default now()
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_now timestamptz := coalesce(p_now, now());
    v_cycle_start timestamptz := public.community_alias_cycle_start(v_now);
    v_settled_count integer := 0;
begin
    with due as (
        select
            c.id,
            coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
            coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
        from public.community_alias_candidates c
        left join public.community_alias_votes v on v.candidate_id = c.id
        where c.status = 'voting'
          and c.vote_close_at is not null
          and c.vote_close_at <= v_now
        group by c.id
    ),
    settled as (
        update public.community_alias_candidates c
           set status = case when d.support_count > d.oppose_count and d.support_count >= 3 then 'approved' else 'rejected' end,
               approved_at = case when d.support_count > d.oppose_count and d.support_count >= 3 then v_now else null end,
               rejected_at = case when d.support_count > d.oppose_count and d.support_count >= 3 then null else v_now end,
               updated_at = v_now
          from due d
         where c.id = d.id
         returning c.id
    )
    select count(*)::integer into v_settled_count from settled;

    return jsonb_build_object(
        'now', v_now,
        'cycle_start', v_cycle_start,
        'settled_count', v_settled_count
    );
end;
$$;

alter table public.community_alias_candidates enable row level security;
alter table public.community_alias_votes enable row level security;

-- Candidates
drop policy if exists community_alias_candidates_select on public.community_alias_candidates;
create policy community_alias_candidates_select
    on public.community_alias_candidates
    for select
    to authenticated
    using (
        status in ('voting', 'approved')
        or submitter_id = auth.uid()
    );

drop policy if exists community_alias_candidates_insert_own on public.community_alias_candidates;
create policy community_alias_candidates_insert_own
    on public.community_alias_candidates
    for insert
    to authenticated
    with check (
        submitter_id = auth.uid()
        and status = 'voting'
        and vote_open_at is not null
        and vote_close_at is not null
    );

-- Votes
drop policy if exists community_alias_votes_select_authenticated on public.community_alias_votes;
create policy community_alias_votes_select_authenticated
    on public.community_alias_votes
    for select
    to authenticated
    using (true);

drop policy if exists community_alias_votes_insert_own on public.community_alias_votes;
create policy community_alias_votes_insert_own
    on public.community_alias_votes
    for insert
    to authenticated
    with check (voter_id = auth.uid());

drop policy if exists community_alias_votes_update_own on public.community_alias_votes;
create policy community_alias_votes_update_own
    on public.community_alias_votes
    for update
    to authenticated
    using (voter_id = auth.uid())
    with check (voter_id = auth.uid());

-- Function grants
grant execute on function public.community_alias_find_candidates_for_dedupe(text, text, integer) to authenticated;
grant execute on function public.community_alias_count_daily_creations(date) to authenticated;
grant execute on function public.community_alias_get_voting_board(integer, integer) to authenticated;
grant execute on function public.community_alias_get_my_song_candidates(text, integer) to authenticated;
grant execute on function public.community_alias_vote(uuid, smallint) to authenticated;
grant execute on function public.community_alias_sync_approved_since(timestamptz, integer) to anon, authenticated;
grant execute on function public.community_alias_cycle_end(timestamptz) to authenticated;
grant execute on function public.community_alias_roll_cycle(timestamptz) to service_role;

-- Optional: schedule minutely settlement job when pg_cron is available.
do $block$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        if exists (select 1 from cron.job where jobname = 'community-alias-roll-cycle-hourly') then
            perform cron.unschedule((select jobid from cron.job where jobname = 'community-alias-roll-cycle-hourly' limit 1));
        end if;
        if exists (select 1 from cron.job where jobname = 'community-alias-roll-cycle-minutely') then
            perform cron.unschedule((select jobid from cron.job where jobname = 'community-alias-roll-cycle-minutely' limit 1));
        end if;

        perform cron.schedule(
            'community-alias-roll-cycle-minutely',
            '* * * * *',
            $cron$select public.community_alias_roll_cycle();$cron$
        );
    end if;
exception
    when undefined_table then
        -- cron.job might not be available in some projects; keep migration idempotent.
        null;
end;
$block$;
