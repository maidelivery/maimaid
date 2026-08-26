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
const staticAssetsOrigin = (() => {
	try {
		return new URL(process.env.NEXT_PUBLIC_STATIC_ASSETS_URL ?? "").origin;
	} catch {
		return null;
	}
})();
const connectSources = ["'self'"];
// Profile avatars are streamed from the backend (`GET /v1/profiles/:id/avatar`),
// so the backend origin has to be an allowed image source too.
const imageSources = ["'self'", "data:", "https://dp4p6x0xfi5o9.cloudfront.net", "https://assets2.lxns.net"];
if (backendOrigin) {
	connectSources.push(backendOrigin);
	imageSources.push(backendOrigin);
}
if (staticAssetsOrigin) {
	connectSources.push(staticAssetsOrigin);
	imageSources.push(staticAssetsOrigin);
}
connectSources.push("https://assets.rhythmeta.org");
imageSources.push("https://assets.rhythmeta.org");

const contentSecurityPolicy = [
	"default-src 'self'",
	"base-uri 'self'",
	"frame-ancestors 'none'",
	"object-src 'none'",
	"form-action 'self'",
	`connect-src ${connectSources.join(" ")}`,
	`img-src ${imageSources.join(" ")}`,
	"font-src 'self' data:",
	"script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'",
	"style-src 'self' 'unsafe-inline'",
].join("; ");

export default function nextConfig(phase: string): NextConfig {
	return {
		output: "export",
		images: {
			unoptimized: true,
			remotePatterns: [
				{ protocol: "https", hostname: "maimaid-assets.rhythmeta.org" },
				{ protocol: "https", hostname: "assets.rhythmeta.org" },
			],
		},
		...(phase === PHASE_DEVELOPMENT_SERVER
			? {
					async rewrites() {
						return [{ source: "/collection/:segment", destination: "/?collection=:segment" }];
					},
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
