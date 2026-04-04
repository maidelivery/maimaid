create table if not exists "auth_session_codes" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references "users"("id") on delete cascade,
  "codeHash" text not null unique,
  "expiresAt" timestamptz not null,
  "consumedAt" timestamptz,
  "createdAt" timestamptz not null default now()
);

create index if not exists "auth_session_codes_userId_idx"
  on "auth_session_codes"("userId");

create index if not exists "auth_session_codes_expiresAt_idx"
  on "auth_session_codes"("expiresAt");

create table if not exists "rate_limit_counters" (
  "id" uuid primary key default gen_random_uuid(),
  "bucket" text not null,
  "keyHash" text not null,
  "windowStart" timestamptz not null,
  "windowEnd" timestamptz not null,
  "count" integer not null default 0,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now()
);

create unique index if not exists "rate_limit_counters_bucket_keyHash_windowStart_key"
  on "rate_limit_counters"("bucket", "keyHash", "windowStart");

create index if not exists "rate_limit_counters_windowEnd_idx"
  on "rate_limit_counters"("windowEnd");
