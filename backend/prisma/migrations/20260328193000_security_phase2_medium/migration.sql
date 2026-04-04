create table if not exists "direct_passkey_challenges" (
  "id" uuid primary key default gen_random_uuid(),
  "tokenHash" text not null unique,
  "challenge" text not null,
  "channel" text not null,
  "expiresAt" timestamptz not null,
  "consumedAt" timestamptz,
  "createdAt" timestamptz not null default now()
);

create index if not exists "direct_passkey_challenges_expiresAt_idx"
  on "direct_passkey_challenges"("expiresAt");
