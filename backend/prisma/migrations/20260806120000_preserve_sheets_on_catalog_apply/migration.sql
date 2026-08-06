-- Preserve sheet rows (and therefore user scores) across catalog applies.
--
-- Previously `applySnapshotPayload` ran `DELETE FROM "sheets"` on every catalog
-- apply and recreated every row. Because "best_scores"."sheetId" and
-- "play_records"."sheetId" reference "sheets"("id") ON DELETE CASCADE, and
-- "sheets"."id" is a bigserial surrogate key, that wiped every user's scores on
-- each apply and the recreated rows got brand new ids.
--
-- Sheets are now upserted on the stable business key
-- ("songIdentifier", "chartType", "difficulty") so ids survive. Sheets that
-- disappear from the upstream catalog are flagged disabled instead of deleted,
-- mirroring how "songs"."disabled" already works, so their scores are retained.

ALTER TABLE "sheets"
  ADD COLUMN IF NOT EXISTS "disabled" boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS "sheets_disabled_idx" ON "sheets"("disabled");
