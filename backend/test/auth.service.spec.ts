import "reflect-metadata";
import type { PrismaClient } from "@prisma/client";
import { describe, expect, it } from "vitest";
import type { Env } from "../src/env.js";
import { AuthService } from "../src/services/auth.service.js";
import { JwtService } from "../src/services/jwt.service.js";

const createEnv = (): Env => ({
	NODE_ENV: "test",
	HOST: "0.0.0.0",
	PORT: 8787,
	APP_PUBLIC_URL: "https://api.example.com",
	CORS_ALLOWED_ORIGINS: "",
	DATABASE_URL: "postgres://localhost:5432/maimaid",
	JWT_ISSUER: "maimaid-backend",
	JWT_AUDIENCE: "maimaid-clients",
	JWT_ACCESS_SECRET: "1234567890123456",
	JWT_ACCESS_TTL_SECONDS: 900,
	JWT_REFRESH_TTL_SECONDS: 60 * 60 * 24 * 30,
	MFA_CHALLENGE_TTL_SECONDS: 300,
	WEBAUTHN_RP_ID: undefined,
	WEBAUTHN_RP_NAME: "maimaid",
	WEBAUTHN_ORIGIN: undefined,
	RESEND_API_KEY: undefined,
	RESEND_FROM_EMAIL: "no-reply@example.com",
	S3_ENDPOINT: undefined,
	S3_PUBLIC_ENDPOINT: undefined,
	S3_REGION: "auto",
	S3_BUCKET: "maimaid-assets",
	S3_ACCESS_KEY_ID: undefined,
	S3_SECRET_ACCESS_KEY: undefined,
	CATALOG_SOURCE_URL: "https://example.com/catalog.json",
	STATIC_SYNC_INTERVAL_HOURS: 6,
});

type AuthServicePrivateAPI = {
	buildAuthActionUrl: (
		action: "verify-email" | "password-reset",
		token: string,
		emailLinkContext?: {
			channel?: "web" | "app";
			redirectUri?: string;
		},
	) => string;
};

describe("AuthService", () => {
	it("builds app auth email links with client=app", () => {
		const env = createEnv();
		const service = new AuthService({} as PrismaClient, new JwtService(env), env);
		const buildAuthActionUrl = (service as unknown as AuthServicePrivateAPI).buildAuthActionUrl.bind(service);

		const url = new URL(
			buildAuthActionUrl("password-reset", "reset-token", {
				channel: "app",
				redirectUri: "maimaid://auth/callback",
			}),
		);

		expect(url.pathname).toBe("/v1/auth/password-reset");
		expect(url.searchParams.get("token")).toBe("reset-token");
		expect(url.searchParams.get("client")).toBe("app");
		expect(url.searchParams.get("redirect_uri")).toBe("maimaid://auth/callback");
	});
});
