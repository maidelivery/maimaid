create table if not exists "user_mfa_backup_codes" (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references "users"("id") on delete cascade,
  "codeHash" text not null,
  "createdAt" timestamptz not null default now(),
  "consumedAt" timestamptz
);

create unique index if not exists "user_mfa_backup_codes_userId_codeHash_key"
  on "user_mfa_backup_codes"("userId", "codeHash");

create index if not exists "user_mfa_backup_codes_userId_consumedAt_idx"
  on "user_mfa_backup_codes"("userId", "consumedAt");
