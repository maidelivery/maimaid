import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const currentFilePath = fileURLToPath(import.meta.url);
const scriptsDirectory = dirname(currentFilePath);
const projectRoot = join(scriptsDirectory, "..");
const outputPath = join(projectRoot, "public", "_headers");

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
	"script-src 'self' 'unsafe-inline'",
	"style-src 'self' 'unsafe-inline'",
].join("; ");

const headers = `/*
  Content-Security-Policy: ${contentSecurityPolicy}
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=(), fullscreen=(self)
`;

writeFileSync(outputPath, headers, "utf8");
console.log(`Generated security headers at ${outputPath}`);
