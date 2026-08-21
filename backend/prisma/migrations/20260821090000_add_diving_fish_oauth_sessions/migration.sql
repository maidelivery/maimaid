create table if not exists "diving_fish_oauth_sessions" (
    "id" uuid primary key default gen_random_uuid(),
    "profileId" uuid not null unique references "profiles"("id") on delete cascade,
    "stateHash" text not null unique,
    "codeVerifier" text not null,
    "status" text not null default 'pending',
    "errorCode" text,
    "expiresAt" timestamptz not null,
    "completedAt" timestamptz,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);

create index if not exists "diving_fish_oauth_sessions_profileId_createdAt_idx"
    on "diving_fish_oauth_sessions"("profileId", "createdAt");
create index if not exists "diving_fish_oauth_sessions_expiresAt_idx"
    on "diving_fish_oauth_sessions"("expiresAt");
