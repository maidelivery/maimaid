alter type "LetterGameRoomStatus" add value if not exists 'closed';

alter table "letter_game_rooms"
add column "closedAt" timestamptz;

create index "letter_game_rooms_status_updatedAt_idx"
on "letter_game_rooms"("status", "updatedAt");
