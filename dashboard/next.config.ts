import type { NextConfig } from "next";
import { PHASE_DEVELOPMENT_SERVER } from "next/constants";

function resolveBackendOrigin() {
	const raw = (process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8787").trim();
	if (!raw) {
		return null;
	}

	try {
		return new URL(raw).origin;
	} catch {
		return null;
	}
}

const backendOrigin = resolveBackendOrigin();
const connectSources = ["'self'"];
if (backendOrigin) {
	connectSources.push(backendOrigin);
}

const contentSecurityPolicy = [
	"default-src 'self'",
	"base-uri 'self'",
	"frame-ancestors 'none'",
	"object-src 'none'",
	"form-action 'self'",
	`connect-src ${connectSources.join(" ")}`,
	"img-src 'self' data: https://dp4p6x0xfi5o9.cloudfront.net",
	"font-src 'self' data:",
	"script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'",
	"style-src 'self' 'unsafe-inline'",
].join("; ");

export default function nextConfig(phase: string): NextConfig {
	return {
		output: "export",
		...(phase === PHASE_DEVELOPMENT_SERVER
			? {
					async headers() {
						return [
							{
								source: "/:path*",
								headers: [
									{
										key: "Content-Security-Policy",
										value: contentSecurityPolicy,
									},
									{
										key: "X-Frame-Options",
										value: "DENY",
									},
									{
										key: "X-Content-Type-Options",
										value: "nosniff",
									},
									{
										key: "Referrer-Policy",
										value: "strict-origin-when-cross-origin",
									},
									{
										key: "Permissions-Policy",
										value: "camera=(), microphone=(), geolocation=(), fullscreen=(self)",
									},
								],
							},
						];
					},
				}
			: {}),
	};
}
