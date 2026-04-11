DO $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM "users"
		WHERE "username" IS NULL
			OR "usernameNormalized" IS NULL
			OR "usernameDiscriminator" IS NULL
	) THEN
		RAISE EXCEPTION 'User handle backfill required before applying this migration. Run backend/scripts/backfill-user-handles.ts first.';
	END IF;
END $$;

ALTER TABLE "users"
ALTER COLUMN "username" SET NOT NULL,
ALTER COLUMN "usernameNormalized" SET NOT NULL,
ALTER COLUMN "usernameDiscriminator" SET NOT NULL;

CREATE UNIQUE INDEX "users_usernameNormalized_usernameDiscriminator_key"
ON "users"("usernameNormalized", "usernameDiscriminator");
