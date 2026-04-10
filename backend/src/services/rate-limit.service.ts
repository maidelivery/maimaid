import { inject, injectable } from "tsyringe";
import type { PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { sha256Hex } from "../lib/crypto.js";
import { AppError } from "../lib/errors.js";

type ConsumeInput = {
	bucket: string;
	key: string;
	limit: number;
	windowSeconds: number;
};

@injectable()
export class RateLimitService {
	constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

	async consume(input: ConsumeInput): Promise<void> {
		if (input.limit < 1 || input.windowSeconds < 1) {
			throw new AppError(500, "rate_limit_config_invalid", "Rate limit configuration is invalid.");
		}

		const normalizedBucket = input.bucket.trim();
		if (!normalizedBucket) {
			throw new AppError(500, "rate_limit_config_invalid", "Rate limit bucket is invalid.");
		}

		const keyHash = await sha256Hex(input.key.trim().toLowerCase() || "unknown");
		const nowMs = Date.now();
		const windowMs = input.windowSeconds * 1000;
		const windowStartMs = Math.floor(nowMs / windowMs) * windowMs;
		const windowStart = new Date(windowStartMs);
		const windowEnd = new Date(windowStartMs + windowMs);

		const row = await this.prisma.rateLimitCounter.upsert({
			where: {
				bucket_keyHash_windowStart: {
					bucket: normalizedBucket,
					keyHash,
					windowStart,
				},
			},
			create: {
				bucket: normalizedBucket,
				keyHash,
				windowStart,
				windowEnd,
				count: 1,
			},
			update: {
				count: {
					increment: 1,
				},
				windowEnd,
			},
			select: {
				count: true,
			},
		});

		if (row.count > input.limit) {
			throw new AppError(429, "rate_limited", "Too many requests. Please try again later.");
		}
	}
}
