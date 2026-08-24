CREATE TABLE "song_collections" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "sortIndex" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    CONSTRAINT "song_collections_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "song_collection_items" (
    "id" UUID NOT NULL,
    "collectionId" UUID NOT NULL,
    "songId" TEXT NOT NULL,
    "chartType" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL,
    "position" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    CONSTRAINT "song_collection_items_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "song_collection_items_collectionId_songId_chartType_difficulty_key"
ON "song_collection_items"("collectionId", "songId", "chartType", "difficulty");
CREATE INDEX "song_collections_userId_sortIndex_idx" ON "song_collections"("userId", "sortIndex");
CREATE INDEX "song_collections_userId_updatedAt_idx" ON "song_collections"("userId", "updatedAt");
CREATE INDEX "song_collection_items_collectionId_position_idx" ON "song_collection_items"("collectionId", "position");
CREATE INDEX "song_collection_items_collectionId_updatedAt_idx" ON "song_collection_items"("collectionId", "updatedAt");

ALTER TABLE "song_collections" ADD CONSTRAINT "song_collections_userId_fkey"
FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "song_collection_items" ADD CONSTRAINT "song_collection_items_collectionId_fkey"
FOREIGN KEY ("collectionId") REFERENCES "song_collections"("id") ON DELETE CASCADE ON UPDATE CASCADE;
