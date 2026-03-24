-- Admin console RPCs for community alias review and maintenance.

create or replace function public.community_alias_admin_has_access()
returns boolean
language sql
stable
as $$
select
    auth.uid() is not null
    and (
        lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '')) in ('admin', 'community_alias_admin')
        or lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'is_admin', 'false')) = 'true'
        or lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'community_alias_admin', 'false')) = 'true'
        or exists (
            select 1
            from jsonb_array_elements_text(
                case jsonb_typeof(auth.jwt() -> 'app_metadata' -> 'roles')
                    when 'array' then auth.jwt() -> 'app_metadata' -> 'roles'
                    else '[]'::jsonb
                end
            ) as roles(role_name)
            where lower(roles.role_name) in ('admin', 'community_alias_admin')
        )
    );
$$;

create or replace function public.community_alias_require_admin()
returns void
language plpgsql
stable
as $$
begin
    if auth.uid() is null then
        raise exception 'Not authenticated';
    end if;

    if not public.community_alias_admin_has_access() then
        raise exception 'Admin permission required';
    end if;
end;
$$;

create or replace function public.community_alias_admin_get_context()
returns table (
    user_id uuid,
    email text,
    is_admin boolean
)
language sql
stable
security definer
set search_path = public
as $$
select
    auth.uid() as user_id,
    coalesce(auth.jwt() ->> 'email', '') as email,
    public.community_alias_admin_has_access() as is_admin;
$$;

create or replace function public.community_alias_admin_dashboard_stats()
returns table (
    total_count integer,
    voting_count integer,
    approved_count integer,
    rejected_count integer,
    closing_soon_count integer,
    expired_voting_count integer,
    today_submissions integer
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    perform public.community_alias_require_admin();

    return query
    select
        count(*)::integer as total_count,
        count(*) filter (where c.status = 'voting')::integer as voting_count,
        count(*) filter (where c.status = 'approved')::integer as approved_count,
        count(*) filter (where c.status = 'rejected')::integer as rejected_count,
        count(*) filter (
            where c.status = 'voting'
              and c.vote_close_at is not null
              and c.vote_close_at between now() and now() + interval '24 hours'
        )::integer as closing_soon_count,
        count(*) filter (
            where c.status = 'voting'
              and c.vote_close_at is not null
              and c.vote_close_at < now()
        )::integer as expired_voting_count,
        count(*) filter (
            where c.submitted_local_date = (now() at time zone 'Asia/Shanghai')::date
        )::integer as today_submissions
    from public.community_alias_candidates c;
end;
$$;

create or replace function public.community_alias_admin_list_candidates(
    p_status text default null,
    p_search text default null,
    p_sort text default 'updated_desc',
    p_limit integer default 30,
    p_offset integer default 0
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    submitter_id uuid,
    status text,
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    support_count integer,
    oppose_count integer,
    total_count integer,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_status text := nullif(trim(coalesce(p_status, '')), '');
    v_search text := nullif(trim(coalesce(p_search, '')), '');
    v_search_norm text := nullif(public.community_alias_normalize(coalesce(p_search, '')), '');
    v_sort text := lower(coalesce(p_sort, 'updated_desc'));
begin
    perform public.community_alias_require_admin();

    return query
    with vote_stats as (
        select
            v.candidate_id,
            coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
            coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
        from public.community_alias_votes v
        group by v.candidate_id
    ),
    filtered as (
        select
            c.id as candidate_id,
            c.song_identifier,
            c.alias_text,
            c.submitter_id,
            c.status,
            c.vote_open_at,
            c.vote_close_at,
            c.approved_at,
            c.rejected_at,
            coalesce(vs.support_count, 0)::integer as support_count,
            coalesce(vs.oppose_count, 0)::integer as oppose_count,
            count(*) over()::integer as total_count,
            c.created_at,
            c.updated_at
        from public.community_alias_candidates c
        left join vote_stats vs on vs.candidate_id = c.id
        where (v_status is null or v_status = 'all' or c.status = v_status)
          and (
            v_search is null
            or c.song_identifier ilike '%' || v_search || '%'
            or c.alias_text ilike '%' || v_search || '%'
            or (v_search_norm is not null and c.alias_norm like '%' || v_search_norm || '%')
          )
    )
    select
        f.candidate_id,
        f.song_identifier,
        f.alias_text,
        f.submitter_id,
        f.status,
        f.vote_open_at,
        f.vote_close_at,
        f.approved_at,
        f.rejected_at,
        f.support_count,
        f.oppose_count,
        f.total_count,
        f.created_at,
        f.updated_at
    from filtered f
    order by
        case when v_sort = 'deadline_asc' then f.vote_close_at end asc nulls last,
        case when v_sort = 'deadline_desc' then f.vote_close_at end desc nulls last,
        case when v_sort = 'votes_desc' then (f.support_count - f.oppose_count) end desc,
        case when v_sort = 'votes_asc' then (f.support_count - f.oppose_count) end asc,
        case when v_sort = 'created_asc' then f.created_at end asc,
        case when v_sort = 'created_desc' then f.created_at end desc,
        case when v_sort = 'updated_asc' then f.updated_at end asc,
        case when v_sort = 'updated_desc' then f.updated_at end desc,
        f.updated_at desc,
        f.created_at desc
    limit greatest(1, least(coalesce(p_limit, 30), 200))
    offset greatest(0, coalesce(p_offset, 0));
end;
$$;

create or replace function public.community_alias_admin_create_candidate(
    p_song_identifier text,
    p_alias_text text,
    p_status text default 'approved'
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    submitter_id uuid,
    status text,
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    support_count integer,
    oppose_count integer,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_now timestamptz := now();
    v_song_identifier text := trim(coalesce(p_song_identifier, ''));
    v_alias_text text := trim(coalesce(p_alias_text, ''));
    v_status text := lower(trim(coalesce(p_status, 'approved')));
    v_alias_norm text;
    v_candidate public.community_alias_candidates%rowtype;
begin
    perform public.community_alias_require_admin();

    if v_song_identifier = '' or v_alias_text = '' or char_length(v_alias_text) > 64 then
        raise exception 'song_identifier and alias_text are required; alias_text must be 1..64 chars';
    end if;

    if v_status not in ('voting', 'approved') then
        raise exception 'Invalid initial status';
    end if;

    v_alias_norm := public.community_alias_normalize(v_alias_text);
    if v_alias_norm = '' then
        raise exception 'alias_text is invalid after normalization';
    end if;

    insert into public.community_alias_candidates (
        song_identifier,
        alias_text,
        alias_norm,
        submitter_id,
        status,
        vote_open_at,
        vote_close_at,
        approved_at,
        rejected_at,
        submitted_local_date,
        submitted_tz_offset_min
    )
    values (
        v_song_identifier,
        v_alias_text,
        v_alias_norm,
        auth.uid(),
        v_status,
        v_now,
        case when v_status = 'voting' then public.community_alias_cycle_end(v_now) else v_now end,
        case when v_status = 'approved' then v_now else null end,
        null,
        (v_now at time zone 'Asia/Shanghai')::date,
        480
    )
    returning * into v_candidate;

    return query
    select
        v_candidate.id,
        v_candidate.song_identifier,
        v_candidate.alias_text,
        v_candidate.submitter_id,
        v_candidate.status,
        v_candidate.vote_open_at,
        v_candidate.vote_close_at,
        v_candidate.approved_at,
        v_candidate.rejected_at,
        0::integer,
        0::integer,
        v_candidate.created_at,
        v_candidate.updated_at;
exception
    when unique_violation then
        raise exception 'Alias already exists for this song in active status';
end;
$$;

create or replace function public.community_alias_admin_set_status(
    p_candidate_id uuid,
    p_status text
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    submitter_id uuid,
    status text,
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    support_count integer,
    oppose_count integer,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_now timestamptz := now();
    v_status text := lower(trim(coalesce(p_status, '')));
begin
    perform public.community_alias_require_admin();

    if v_status not in ('voting', 'approved', 'rejected') then
        raise exception 'Invalid status';
    end if;

    update public.community_alias_candidates c
       set status = v_status,
           vote_open_at = case
               when v_status = 'voting' and c.status <> 'voting' then v_now
               when v_status = 'voting' then coalesce(c.vote_open_at, v_now)
               else c.vote_open_at
           end,
           vote_close_at = case
               when v_status = 'voting' and (c.status <> 'voting' or c.vote_close_at is null or c.vote_close_at <= v_now)
                   then public.community_alias_cycle_end(v_now)
               when v_status = 'voting' then c.vote_close_at
               else c.vote_close_at
           end,
           approved_at = case when v_status = 'approved' then v_now else null end,
           rejected_at = case when v_status = 'rejected' then v_now else null end,
           updated_at = v_now
     where c.id = p_candidate_id;

    if not found then
        raise exception 'Candidate not found';
    end if;

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
        c.id,
        c.song_identifier,
        c.alias_text,
        c.submitter_id,
        c.status,
        c.vote_open_at,
        c.vote_close_at,
        c.approved_at,
        c.rejected_at,
        coalesce(vs.support_count, 0)::integer,
        coalesce(vs.oppose_count, 0)::integer,
        c.created_at,
        c.updated_at
    from public.community_alias_candidates c
    left join vote_stats vs on vs.candidate_id = c.id
    where c.id = p_candidate_id;
end;
$$;

create or replace function public.community_alias_admin_update_vote_window(
    p_candidate_id uuid,
    p_vote_close_at timestamptz
)
returns table (
    candidate_id uuid,
    song_identifier text,
    alias_text text,
    submitter_id uuid,
    status text,
    vote_open_at timestamptz,
    vote_close_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    support_count integer,
    oppose_count integer,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_now timestamptz := now();
    v_status text;
begin
    perform public.community_alias_require_admin();

    if p_vote_close_at is null or p_vote_close_at <= v_now then
        raise exception 'vote_close_at must be later than now';
    end if;

    select c.status
      into v_status
    from public.community_alias_candidates c
    where c.id = p_candidate_id;

    if v_status is null then
        raise exception 'Candidate not found';
    end if;

    if v_status <> 'voting' then
        raise exception 'Only voting candidates can update vote window';
    end if;

    update public.community_alias_candidates c
       set vote_open_at = coalesce(c.vote_open_at, v_now),
           vote_close_at = p_vote_close_at,
           updated_at = v_now
     where c.id = p_candidate_id;

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
        c.id,
        c.song_identifier,
        c.alias_text,
        c.submitter_id,
        c.status,
        c.vote_open_at,
        c.vote_close_at,
        c.approved_at,
        c.rejected_at,
        coalesce(vs.support_count, 0)::integer,
        coalesce(vs.oppose_count, 0)::integer,
        c.created_at,
        c.updated_at
    from public.community_alias_candidates c
    left join vote_stats vs on vs.candidate_id = c.id
    where c.id = p_candidate_id;
end;
$$;

create or replace function public.community_alias_admin_roll_cycle(
    p_now timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.community_alias_require_admin();
    return public.community_alias_roll_cycle(coalesce(p_now, now()));
end;
$$;

grant execute on function public.community_alias_admin_has_access() to authenticated;
grant execute on function public.community_alias_admin_get_context() to authenticated;
grant execute on function public.community_alias_admin_dashboard_stats() to authenticated;
grant execute on function public.community_alias_admin_list_candidates(text, text, text, integer, integer) to authenticated;
grant execute on function public.community_alias_admin_create_candidate(text, text, text) to authenticated;
grant execute on function public.community_alias_admin_set_status(uuid, text) to authenticated;
grant execute on function public.community_alias_admin_update_vote_window(uuid, timestamptz) to authenticated;
grant execute on function public.community_alias_admin_roll_cycle(timestamptz) to authenticated;
