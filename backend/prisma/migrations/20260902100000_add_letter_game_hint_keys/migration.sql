ALTER TABLE "letter_game_player_facts"
ADD COLUMN "hintKey" text NOT NULL DEFAULT '';

ALTER TABLE "letter_game_player_facts"
DROP CONSTRAINT IF EXISTS "letter_game_player_facts_matchId_songId_userId_factType_visibility_key";

CREATE UNIQUE INDEX "letter_game_player_facts_matchId_songId_userId_factType_visibility_hintKey_key"
ON "letter_game_player_facts"("matchId", "songId", "userId", "factType", "visibility", "hintKey");
