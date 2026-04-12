alter table "users"
  alter column "passwordHash" drop not null,
  add column if not exists "opaqueRegistrationRecord" text,
  add column if not exists "passwordFingerprintHash" text;

create table if not exists "opaque_login_challenges" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references "users"("id") on delete cascade,
  "tokenHash" text not null unique,
  "serverLoginState" text not null,
  "expiresAt" timestamptz not null,
  "consumedAt" timestamptz,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now()
);

create index if not exists "opaque_login_challenges_userId_createdAt_idx"
  on "opaque_login_challenges"("userId", "createdAt");

create index if not exists "opaque_login_challenges_expiresAt_idx"
  on "opaque_login_challenges"("expiresAt");
