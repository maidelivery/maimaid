-- Fix: "column reference \"candidate_id\" is ambiguous" in community_alias_vote RPC.
-- Root cause is name conflict in PL/pgSQL context. Rebuild function with explicit conflict handling
-- and avoid ambiguous references in RETURN QUERY.

create or replace function public.community_alias_vote(
    p_candidate_id uuid,
    p_vote integer
)
returns table (
    candidate_id uuid,
    support_count integer,
    oppose_count integer,
    my_vote integer
)
language plpgsql
security definer
set search_path = public
as $$
#variable_conflict use_column
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
    values (p_candidate_id, v_user_id, p_vote::smallint)
    on conflict on constraint community_alias_votes_candidate_id_voter_id_key
    do update set vote = excluded.vote, updated_at = now();

    return query
    with vote_stats as (
        select
            coalesce(sum(case when v.vote = 1 then 1 else 0 end), 0)::integer as support_count,
            coalesce(sum(case when v.vote = -1 then 1 else 0 end), 0)::integer as oppose_count
        from public.community_alias_votes v
        where v.candidate_id = p_candidate_id
    )
    select
        c.id,
        coalesce(vs.support_count, 0)::integer,
        coalesce(vs.oppose_count, 0)::integer,
        p_vote
    from public.community_alias_candidates c
    left join vote_stats vs on true
    where c.id = p_candidate_id;
end;
$$;

grant execute on function public.community_alias_vote(uuid, integer) to authenticated;
