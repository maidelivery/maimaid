# community-alias-submit

Edge Function for community alias submission with hybrid dedupe:

1. SQL dedupe check (exact/high confidence)
2. Gray-zone LLM check (OpenAI-compatible API)
3. Daily quota check (only successful new candidate creation counts)
4. Candidate insert (`voting`) with `vote_open_at = now` and `vote_close_at = community_alias_cycle_end(now)`

## Required secrets

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` (required for user-context auth/RLS)
- `SUPABASE_SERVICE_ROLE_KEY` (optional fallback only)
- `THIRD_PARTY_LLM_BASE_URL`
- `THIRD_PARTY_LLM_API_KEY`
- `THIRD_PARTY_LLM_MODEL`

## Deploy

```bash
supabase functions deploy community-alias-submit --no-verify-jwt
```
