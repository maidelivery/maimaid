-- Compatibility hotfix:
-- PostgREST may pass numeric params as integer for RPC calls.
-- Provide an integer overload to avoid function resolution failures.

create or replace function public.community_alias_vote(
    p_candidate_id uuid,
    p_vote integer
)
returns table (
    candidate_id uuid,
    support_count integer,
    oppose_count integer,
    my_vote smallint
)
language sql
security definer
set search_path = public
as $$
    select *
    from public.community_alias_vote(p_candidate_id, p_vote::smallint);
$$;

grant execute on function public.community_alias_vote(uuid, integer) to authenticated;
