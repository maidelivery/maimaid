ALTER TABLE "song_collections"
  ADD COLUMN "clientUpdatedAt" TIMESTAMP(3);

ALTER TABLE "song_collection_items"
  ADD COLUMN "clientUpdatedAt" TIMESTAMP(3);

WITH ranked AS (
  SELECT "id",
         row_number() OVER (
           PARTITION BY "collectionId", trim("songId"), lower(trim("chartType")), lower(trim("difficulty"))
           ORDER BY ("deletedAt" IS NULL) DESC, "updatedAt" DESC, "id"
         ) AS "rowNumber"
    FROM "song_collection_items"
)
DELETE FROM "song_collection_items" item
 USING ranked
 WHERE item."id" = ranked."id"
   AND ranked."rowNumber" > 1;

UPDATE "song_collection_items"
   SET "songId" = trim("songId"),
       "chartType" = lower(trim("chartType")),
       "difficulty" = lower(trim("difficulty"));

CREATE INDEX "song_collections_userId_clientUpdatedAt_idx"
  ON "song_collections"("userId", "clientUpdatedAt");

CREATE INDEX "song_collection_items_collectionId_clientUpdatedAt_idx"
  ON "song_collection_items"("collectionId", "clientUpdatedAt");
