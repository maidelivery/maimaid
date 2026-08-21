import { z } from "zod";

/** Treats a blank value the same as an absent one. */
const optionalString = z
	.string()
	.optional()
	.transform((value) => {
		const trimmed = value?.trim();
		return trimmed ? trimmed : undefined;
	});

const optionalUrl = optionalString.pipe(z.url().optional());

const EnvSchema = z.object({
	NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
	HOST: z.string().min(1).default("0.0.0.0"),
	PORT: z.coerce.number().int().positive().default(8787),
	APP_PUBLIC_URL: z.url().optional(),
	CORS_ALLOWED_ORIGINS: z.string().default(""),
	DIVING_FISH_OAUTH_CLIENT_ID: z.string().min(1).default("5b79b87f22855b80ee35243eeec07916"),
	DIVING_FISH_OAUTH_CLIENT_SECRET: optionalString,
	DATABASE_URL: z.string().min(1),
	JWT_ISSUER: z.string().min(1).default("maimaid-backend"),
	JWT_AUDIENCE: z.string().min(1).default("maimaid-clients"),
	JWT_ACCESS_SECRET: z.string().min(16),
	JWT_ACCESS_TTL_SECONDS: z.coerce.number().int().positive().default(900),
	JWT_REFRESH_TTL_SECONDS: z.coerce
		.number()
		.int()
		.positive()
		.default(60 * 60 * 24 * 30),
	OPAQUE_SERVER_SETUP: z.string().min(1),
	MFA_CHALLENGE_TTL_SECONDS: z.coerce.number().int().positive().default(300),
	WEBAUTHN_RP_ID: z.string().optional(),
	WEBAUTHN_RP_NAME: z.string().default("maimaid"),
	WEBAUTHN_ORIGIN: z.url().optional(),
	RESEND_API_KEY: z.string().optional(),
	RESEND_FROM_EMAIL: z.email().default("no-reply@example.com"),
	// `.env` files carry these as empty strings when unset rather than omitting
	// them, and "" is not the same as undefined downstream: S3_PUBLIC_ENDPOINT is
	// read as `S3_PUBLIC_ENDPOINT ?? S3_ENDPOINT`, so an empty value would win the
	// `??` and silently disable storage even with valid credentials.
	S3_ENDPOINT: optionalString,
	// Only for setups that sign on one host and serve on another, as MinIO did.
	// R2 uses one host, so leave it unset there.
	S3_PUBLIC_ENDPOINT: optionalString,
	// R2 only accepts "auto".
	S3_REGION: z.string().default("auto"),
	S3_BUCKET: z.string().min(1).default("maimaid-assets"),
	S3_ACCESS_KEY_ID: optionalString,
	S3_SECRET_ACCESS_KEY: optionalString,
	// Static bundles use a separate public bucket so avatar objects can remain private.
	S3_STATIC_BUNDLE_BUCKET: optionalString,
	S3_STATIC_BUNDLE_PUBLIC_BASE_URL: optionalUrl,
	CATALOG_SOURCE_URL: z.url().optional(),
	STATIC_SYNC_INTERVAL_HOURS: z.coerce.number().int().positive().default(6),
	// pg_cron enqueues into "job_queue" but nothing consumed it, so scheduled
	// catalog syncs and bundle builds never ran. This runs a consumer inside the
	// API process. Off by default: a bundle build fetches every upstream source
	// and recomputes chart stats, which is the heaviest thing this server does,
	// so enabling it is a deliberate choice about the host's capacity.
	JOB_DISPATCHER_ENABLED: z
		.enum(["true", "false"])
		.default("false")
		.transform((value) => value === "true"),
	JOB_DISPATCHER_INTERVAL_SECONDS: z.coerce.number().int().positive().default(60),
	JOB_DISPATCHER_BATCH_SIZE: z.coerce.number().int().positive().max(50).default(5),
	// Shared secret for `/internal/*`, so the GitHub Actions bundle builder can
	// authenticate without holding a user account. Unset means those routes accept
	// only an admin JWT, exactly as before. Long enough that guessing is not a
	// concern, since this grants what an admin token grants on those routes.
	INTERNAL_JOB_TOKEN: optionalString.refine((value) => value === undefined || value.length >= 32, {
		message: "INTERNAL_JOB_TOKEN must be at least 32 characters.",
	}),
});

export type Env = z.infer<typeof EnvSchema>;

export const parseEnv = (input: unknown): Env => EnvSchema.parse(input);
