CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS songs_title_trgm_idx
  ON "songs"
  USING gin ("title" gin_trgm_ops);

CREATE INDEX IF NOT EXISTS songs_artist_trgm_idx
  ON "songs"
  USING gin ("artist" gin_trgm_ops);

CREATE INDEX IF NOT EXISTS songs_song_identifier_trgm_idx
  ON "songs"
  USING gin ("songIdentifier" gin_trgm_ops);
