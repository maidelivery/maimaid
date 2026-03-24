-- Intentionally expose auth email existence so the app can provide
-- explicit validation for signup and password recovery flows.

create or replace function public.auth_email_exists(p_email text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
select exists(
    select 1
    from auth.users
    where email is not null
      and lower(email) = lower(nullif(btrim(p_email), ''))
);
$$;

revoke all on function public.auth_email_exists(text) from public;
grant execute on function public.auth_email_exists(text) to anon, authenticated;
