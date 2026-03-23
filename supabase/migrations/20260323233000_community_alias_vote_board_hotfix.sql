-- Hotfix: hide expired voting items from board and make vote RPC return robustly.

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
  and (c.vote_open_at is null or now() >= c.vote_open_at)
  and (c.vote_close_at is null or now() <= c.vote_close_at)
order by c.vote_close_at asc, c.created_at desc
limit greatest(1, least(coalesce(p_limit, 100), 200))
offset greatest(0, coalesce(p_offset, 0));
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
        c.id as candidate_id,
        coalesce(vs.support_count, 0)::integer as support_count,
        coalesce(vs.oppose_count, 0)::integer as oppose_count,
        p_vote as my_vote
    from public.community_alias_candidates c
    left join vote_stats vs on vs.candidate_id = c.id
    where c.id = p_candidate_id;
end;
$$;
