create type "MfaChallengePurpose" as enum ('login', 'passkey_registration');

alter table "play_records"
  add column if not exists "updatedAt" timestamptz not null default now();

create table if not exists "user_totp_credentials" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null unique references "users"("id") on delete cascade,
  "secretBase32" text not null,
  "enabledAt" timestamptz,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now()
);

create index if not exists "user_totp_credentials_enabledAt_idx" on "user_totp_credentials"("enabledAt");

create table if not exists "user_passkey_credentials" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references "users"("id") on delete cascade,
  "credentialId" text not null unique,
  "publicKey" bytea not null,
  "counter" integer not null default 0,
  "transports" text[] not null default '{}',
  "name" text,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now()
);

create index if not exists "user_passkey_credentials_userId_idx" on "user_passkey_credentials"("userId");

create table if not exists "mfa_challenges" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references "users"("id") on delete cascade,
  "tokenHash" text not null unique,
  "purpose" "MfaChallengePurpose" not null,
  "channel" text not null,
  "challenge" text,
  "passkeyAllowIds" text[] not null default '{}',
  "expiresAt" timestamptz not null,
  "consumedAt" timestamptz,
  "createdAt" timestamptz not null default now()
);

create index if not exists "mfa_challenges_userId_purpose_createdAt_idx"
  on "mfa_challenges"("userId", "purpose", "createdAt");
create index if not exists "mfa_challenges_expiresAt_idx" on "mfa_challenges"("expiresAt");

create table if not exists "sync_events" (
  "revision" bigserial primary key,
  "userId" uuid not null references "users"("id") on delete cascade,
  "profileId" uuid,
  "entityType" text not null,
  "entityId" text not null,
  "op" text not null,
  "payloadJson" jsonb,
  "createdAt" timestamptz not null default now()
);

create index if not exists "sync_events_userId_revision_idx" on "sync_events"("userId", "revision");
create index if not exists "sync_events_profileId_revision_idx" on "sync_events"("profileId", "revision");

create table if not exists "sync_mutations" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references "users"("id") on delete cascade,
  "idempotencyKey" text not null,
  "resultJson" jsonb not null,
  "appliedAt" timestamptz not null default now(),
  unique ("userId", "idempotencyKey")
);

create index if not exists "sync_mutations_appliedAt_idx" on "sync_mutations"("appliedAt");

create table if not exists "static_sources" (
  "id" uuid primary key default gen_random_uuid(),
  "category" text not null unique,
  "activeUrl" text not null,
  "fallbackUrls" text[] not null default '{}',
  "enabled" boolean not null default true,
  "metadataJson" jsonb,
  "createdAt" timestamptz not null default now(),
  "updatedAt" timestamptz not null default now()
);

create index if not exists "static_sources_enabled_category_idx" on "static_sources"("enabled", "category");

create table if not exists "static_bundles" (
  "id" bigserial primary key,
  "version" text not null unique,
  "md5" text not null,
  "payloadJson" jsonb not null,
  "sourceMeta" jsonb,
  "active" boolean not null default false,
  "createdAt" timestamptz not null default now(),
  "activatedAt" timestamptz
);

create index if not exists "static_bundles_active_createdAt_idx" on "static_bundles"("active", "createdAt");
