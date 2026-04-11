DO $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM "users"
		WHERE "username" IS NULL
			OR "username_normalized" IS NULL
			OR "username_discriminator" IS NULL
	) THEN
		RAISE EXCEPTION 'User handle backfill required before applying this migration. Run backend/scripts/backfill-user-handles.ts first.';
	END IF;
END $$;

ALTER TABLE "users"
ALTER COLUMN "username" SET NOT NULL,
ALTER COLUMN "username_normalized" SET NOT NULL,
ALTER COLUMN "username_discriminator" SET NOT NULL;

CREATE UNIQUE INDEX "users_username_normalized_username_discriminator_key"
ON "users"("username_normalized", "username_discriminator");
