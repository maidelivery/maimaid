import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { AppError } from "../src/lib/errors.js";
import {
	assignUserHandle,
	buildUsernameBaseFromEmail,
	USERNAME_DISCRIMINATOR_MAX,
} from "../src/lib/user-handle.js";

function createUserHandleClient(discriminators: string[]) {
	return {
		user: {
			findMany: vi.fn().mockResolvedValue(discriminators.map((usernameDiscriminator) => ({ usernameDiscriminator }))),
		},
	};
}

describe("user handle helpers", () => {
	it("allocates the lowest available discriminator for duplicate usernames", async () => {
		const client = createUserHandleClient(["0001", "0003"]);

		const result = await assignUserHandle(client as never, {
			requestedUsername: "Alice",
		});

		expect(result).toEqual({
			username: "Alice",
			usernameNormalized: "alice",
			usernameDiscriminator: "0002",
		});
	});

	it("keeps the discriminator when only the username casing changes", async () => {
		const client = createUserHandleClient(["0001", "0002"]);

		const result = await assignUserHandle(client as never, {
			requestedUsername: "ALICE",
			existingUser: {
				id: "user-1",
				usernameNormalized: "alice",
				usernameDiscriminator: "0042",
			},
		});

		expect(result).toEqual({
			username: "ALICE",
			usernameNormalized: "alice",
			usernameDiscriminator: "0042",
		});
		expect(client.user.findMany).not.toHaveBeenCalled();
	});

	it("reallocates the next available discriminator when moving to an occupied username", async () => {
		const client = createUserHandleClient(["0001", "0002"]);

		const result = await assignUserHandle(client as never, {
			requestedUsername: "Bob",
			existingUser: {
				id: "user-1",
				usernameNormalized: "alice",
				usernameDiscriminator: "0042",
			},
		});

		expect(result).toEqual({
			username: "Bob",
			usernameNormalized: "bob",
			usernameDiscriminator: "0003",
		});
	});

	it("throws when all discriminator slots are exhausted", async () => {
		const client = createUserHandleClient(
			Array.from({ length: USERNAME_DISCRIMINATOR_MAX }, (_, index) => String(index + 1).padStart(4, "0")),
		);

		await expect(
			assignUserHandle(client as never, {
				requestedUsername: "Full",
			}),
		).rejects.toMatchObject<AppError>({
			code: "username_slots_exhausted",
			status: 409,
		});
	});

	it("falls back to user for short email prefixes during backfill", () => {
		expect(buildUsernameBaseFromEmail("x+1@example.com")).toBe("x1");
		expect(buildUsernameBaseFromEmail("!@example.com")).toBe("user");
		expect(buildUsernameBaseFromEmail("a@example.com")).toBe("user");
	});
});
