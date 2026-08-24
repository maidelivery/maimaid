CREATE INDEX IF NOT EXISTS "play_records_profileId_sheetId_playTime_idx"
ON "play_records"("profileId", "sheetId", "playTime");
