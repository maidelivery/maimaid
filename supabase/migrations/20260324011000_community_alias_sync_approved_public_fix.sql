-- Allow approved community aliases to sync for anonymous users as well.
-- The table RLS on community_alias_candidates is authenticated-only, so
-- this RPC must run as SECURITY DEFINER and only expose approved rows.

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
security definer
set search_path = public
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

grant execute on function public.community_alias_sync_approved_since(timestamptz, integer) to anon, authenticated;
