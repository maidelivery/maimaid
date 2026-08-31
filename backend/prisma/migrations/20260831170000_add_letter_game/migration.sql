create type "LetterGameRoomVisibility" as enum ('public', 'private');
create type "LetterGameHostMode" as enum ('fixed', 'rotate');
create type "LetterGameRoomStatus" as enum ('open');
create type "LetterGameMemberStatus" as enum ('pending', 'accepted', 'left', 'kicked');
create type "LetterGameMatchStatus" as enum ('active', 'finished', 'abandoned');
create type "LetterGameSongStatus" as enum ('active', 'completed');
create type "LetterGameCompletionReason" as enum ('guessed', 'all_characters_revealed');
create type "LetterGameHintVisibility" as enum ('public', 'private');
create type "LetterGameHintType" as enum ('version', 'constant', 'white_chart');

create table "letter_game_rooms" (
    "id" uuid primary key default gen_random_uuid(),
    "code" varchar(6) not null unique,
    "visibility" "LetterGameRoomVisibility" not null,
    "hostMode" "LetterGameHostMode" not null default 'fixed',
    "hostUserId" uuid not null references "users"("id") on delete cascade,
    "status" "LetterGameRoomStatus" not null default 'open',
    "turnDurationSeconds" integer not null default 30,
    "stalledRoundLimit" integer not null default 3,
    "songCountOverride" integer,
    "publicHintCost" integer not null default 5,
    "privateHintCost" integer not null default 10,
    "selectionMode" text not null default 'filtered_random',
    "selectionConfig" jsonb not null default '{}',
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now()
);
create index "letter_game_rooms_visibility_updatedAt_idx" on "letter_game_rooms"("visibility", "updatedAt");
create index "letter_game_rooms_hostUserId_idx" on "letter_game_rooms"("hostUserId");

create table "letter_game_room_members" (
    "id" uuid primary key default gen_random_uuid(),
    "roomId" uuid not null references "letter_game_rooms"("id") on delete cascade,
    "userId" uuid not null references "users"("id") on delete cascade,
    "seatOrder" integer not null default 0,
    "status" "LetterGameMemberStatus" not null default 'pending',
    "joinedAt" timestamptz not null default now(),
    "approvedAt" timestamptz,
    "leftAt" timestamptz,
    "kickedAt" timestamptz,
    "lastSeenAt" timestamptz,
    unique ("roomId", "userId")
);
create index "letter_game_room_members_roomId_status_seatOrder_idx" on "letter_game_room_members"("roomId", "status", "seatOrder");
create index "letter_game_room_members_userId_status_idx" on "letter_game_room_members"("userId", "status");

create table "letter_game_matches" (
    "id" uuid primary key default gen_random_uuid(),
    "roomId" uuid not null references "letter_game_rooms"("id") on delete cascade,
    "sequence" integer not null,
    "status" "LetterGameMatchStatus" not null default 'active',
    "sourceType" text not null,
    "sourceConfig" jsonb not null default '{}',
    "hostUserId" uuid not null references "users"("id") on delete restrict,
    "turnOrder" jsonb not null default '[]',
    "currentTurnIndex" integer not null default 0,
    "noProgressRounds" integer not null default 0,
    "revision" integer not null default 0,
    "turnDeadline" timestamptz,
    "startedAt" timestamptz not null default now(),
    "endedAt" timestamptz,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("roomId", "sequence")
);
create index "letter_game_matches_roomId_status_idx" on "letter_game_matches"("roomId", "status");
create index "letter_game_matches_status_turnDeadline_idx" on "letter_game_matches"("status", "turnDeadline");

create table "letter_game_match_players" (
    "id" uuid primary key default gen_random_uuid(),
    "matchId" uuid not null references "letter_game_matches"("id") on delete cascade,
    "userId" uuid not null references "users"("id") on delete cascade,
    "turnOrder" integer not null,
    "score" integer not null default 0,
    "scoringEligible" boolean not null default true,
    "status" text not null default 'active',
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("matchId", "userId"),
    unique ("matchId", "turnOrder")
);
create index "letter_game_match_players_userId_matchId_idx" on "letter_game_match_players"("userId", "matchId");

create table "letter_game_match_songs" (
    "id" uuid primary key default gen_random_uuid(),
    "matchId" uuid not null references "letter_game_matches"("id") on delete cascade,
    "slotId" text not null,
    "songIdentifier" text not null,
    "title" text not null,
    "aliases" jsonb not null default '[]',
    "revealedIndices" jsonb not null default '[]',
    "status" "LetterGameSongStatus" not null default 'active',
    "completionReason" "LetterGameCompletionReason",
    "completedByUserId" uuid references "users"("id") on delete set null,
    "completedAt" timestamptz,
    "createdAt" timestamptz not null default now(),
    "updatedAt" timestamptz not null default now(),
    unique ("matchId", "slotId")
);
create index "letter_game_match_songs_matchId_status_idx" on "letter_game_match_songs"("matchId", "status");

create table "letter_game_player_facts" (
    "id" uuid primary key default gen_random_uuid(),
    "matchId" uuid not null references "letter_game_matches"("id") on delete cascade,
    "songId" uuid not null references "letter_game_match_songs"("id") on delete cascade,
    "userId" uuid not null references "users"("id") on delete cascade,
    "factType" "LetterGameHintType" not null,
    "visibility" "LetterGameHintVisibility" not null,
    "value" jsonb not null,
    "cost" integer not null,
    "createdAt" timestamptz not null default now(),
    unique ("matchId", "songId", "userId", "factType", "visibility")
);
create index "letter_game_player_facts_matchId_userId_idx" on "letter_game_player_facts"("matchId", "userId");

create table "letter_game_actions" (
    "id" uuid primary key default gen_random_uuid(),
    "matchId" uuid not null references "letter_game_matches"("id") on delete cascade,
    "actorId" uuid not null references "users"("id") on delete cascade,
    "idempotencyKey" text not null,
    "sequence" integer not null,
    "actionType" text not null,
    "payload" jsonb not null,
    "result" jsonb not null,
    "createdAt" timestamptz not null default now(),
    unique ("matchId", "actorId", "idempotencyKey")
);
create index "letter_game_actions_matchId_sequence_idx" on "letter_game_actions"("matchId", "sequence");
create index "letter_game_actions_actorId_createdAt_idx" on "letter_game_actions"("actorId", "createdAt");
