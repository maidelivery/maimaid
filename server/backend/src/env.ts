import { z } from "zod";

const EnvSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  HOST: z.string().min(1).default("0.0.0.0"),
  PORT: z.coerce.number().int().positive().default(8787),
  APP_PUBLIC_URL: z.string().url().optional(),
  CORS_ALLOWED_ORIGINS: z.string().default(""),
  DATABASE_URL: z.string().min(1),
  JWT_ISSUER: z.string().min(1).default("maimaid-backend"),
  JWT_AUDIENCE: z.string().min(1).default("maimaid-clients"),
  JWT_ACCESS_SECRET: z.string().min(16),
  JWT_ACCESS_TTL_SECONDS: z.coerce.number().int().positive().default(900),
  JWT_REFRESH_TTL_SECONDS: z.coerce.number().int().positive().default(60 * 60 * 24 * 30),
  MFA_CHALLENGE_TTL_SECONDS: z.coerce.number().int().positive().default(300),
  WEBAUTHN_RP_ID: z.string().optional(),
  WEBAUTHN_RP_NAME: z.string().default("maimaid"),
  WEBAUTHN_ORIGIN: z.string().url().optional(),
  RESEND_API_KEY: z.string().optional(),
  RESEND_FROM_EMAIL: z.string().email().default("no-reply@example.com"),
  S3_ENDPOINT: z.string().optional(),
  S3_PUBLIC_ENDPOINT: z.string().optional(),
  S3_REGION: z.string().default("auto"),
  S3_BUCKET: z.string().min(1).default("maimaid-assets"),
  S3_ACCESS_KEY_ID: z.string().optional(),
  S3_SECRET_ACCESS_KEY: z.string().optional(),
  CATALOG_SOURCE_URL: z.string().url().default("https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json"),
  STATIC_SYNC_INTERVAL_HOURS: z.coerce.number().int().positive().default(6)
});

export type Env = z.infer<typeof EnvSchema>;

let envCache: Env | null = null;

export const getEnv = (): Env => {
  if (envCache) {
    return envCache;
  }

  envCache = EnvSchema.parse(process.env);
  return envCache;
};
