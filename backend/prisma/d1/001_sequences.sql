CREATE TABLE IF NOT EXISTS "d1_sequences" (
  "name" TEXT NOT NULL PRIMARY KEY,
  "value" INTEGER NOT NULL
);

INSERT INTO "d1_sequences" ("name", "value") VALUES
  ('CatalogSnapshot', COALESCE((SELECT MAX("id") FROM "catalog_snapshots"), 0)),
  ('Sheet', COALESCE((SELECT MAX("id") FROM "sheets"), 0)),
  ('Alias', COALESCE((SELECT MAX("id") FROM "aliases"), 0)),
  ('SyncEvent', COALESCE((SELECT MAX("revision") FROM "sync_events"), 0)),
  ('StaticBundle', COALESCE((SELECT MAX("id") FROM "static_bundles"), 0)),
  ('ChartFitSnapshot', COALESCE((SELECT MAX("id") FROM "chart_fit_snapshots"), 0)),
  ('JobQueue', COALESCE((SELECT MAX("id") FROM "job_queue"), 0))
ON CONFLICT ("name") DO UPDATE SET "value" = MAX("value", excluded."value");
