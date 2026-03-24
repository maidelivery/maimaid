-- Enrich admin candidate list with submitter email for operations UI.

drop function if exists public.community_alias_admin_list_candidates(text, text, text, integer, integer);

create function public.community_alias_admin_list_candidates(
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
    submitter_email text,
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
language sql
stable
security definer
set search_path = public
as $$
with _guard as (
    select public.community_alias_require_admin()
),
params as (
    select
        nullif(trim(coalesce(p_status, '')), '') as v_status,
        nullif(trim(coalesce(p_search, '')), '') as v_search,
        nullif(public.community_alias_normalize(coalesce(p_search, '')), '') as v_search_norm,
        lower(coalesce(p_sort, 'updated_desc')) as v_sort
),
vote_stats as (
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
            u.email as submitter_email,
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
        cross join params p
        cross join _guard g
        left join vote_stats vs on vs.candidate_id = c.id
        left join auth.users u on u.id = c.submitter_id
        where (p.v_status is null or p.v_status = 'all' or c.status = p.v_status)
          and (
            p.v_search is null
            or c.song_identifier ilike '%' || p.v_search || '%'
            or c.alias_text ilike '%' || p.v_search || '%'
            or u.email ilike '%' || p.v_search || '%'
            or (p.v_search_norm is not null and c.alias_norm like '%' || p.v_search_norm || '%')
          )
)
select
        f.candidate_id::uuid,
        f.song_identifier::text,
        f.alias_text::text,
        f.submitter_id::uuid,
        f.submitter_email::text,
        f.status::text,
        f.vote_open_at::timestamptz,
        f.vote_close_at::timestamptz,
        f.approved_at::timestamptz,
        f.rejected_at::timestamptz,
        f.support_count::integer,
        f.oppose_count::integer,
        f.total_count::integer,
        f.created_at::timestamptz,
        f.updated_at::timestamptz
    from filtered f
    cross join params p
    order by
        case when p.v_sort = 'deadline_asc' then f.vote_close_at end asc nulls last,
        case when p.v_sort = 'deadline_desc' then f.vote_close_at end desc nulls last,
        case when p.v_sort = 'votes_desc' then (f.support_count - f.oppose_count) end desc,
        case when p.v_sort = 'votes_asc' then (f.support_count - f.oppose_count) end asc,
        case when p.v_sort = 'created_asc' then f.created_at end asc,
        case when p.v_sort = 'created_desc' then f.created_at end desc,
        case when p.v_sort = 'updated_asc' then f.updated_at end asc,
        case when p.v_sort = 'updated_desc' then f.updated_at end desc,
        f.updated_at desc,
        f.created_at desc
    limit greatest(1, least(coalesce(p_limit, 30), 200))
    offset greatest(0, coalesce(p_offset, 0));
$$;

grant execute on function public.community_alias_admin_list_candidates(text, text, text, integer, integer) to authenticated;
