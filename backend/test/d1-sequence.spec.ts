import { describe, expect, it } from "vitest";
import { D1SequenceStorage, type D1SequenceBinding } from "../.worker/d1-sequence.js";

const createBinding = (): D1SequenceBinding => {
	const values = new Map<string, number>([
		["SyncEvent", 20],
		["Alias", 100],
	]);
	let count = 0;
	let model = "";
	const statement = {
		bind(nextCount: unknown, nextModel: unknown) {
			count = Number(nextCount);
			model = String(nextModel);
			return statement;
		},
		async first<T>() {
			const value = (values.get(model) ?? 0) + count;
			values.set(model, value);
			return { value } as T;
		},
	};
	return { prepare: () => statement };
};

describe("D1SequenceStorage", () => {
	it("assigns the next value to a create", async () => {
		const storage = new D1SequenceStorage(createBinding());
		const result = await storage.prepareArgs("SyncEvent", "create", { data: { entityType: "profile" } });

		expect(result).toEqual({ data: { revision: 21n, entityType: "profile" } });
	});

	it("reserves one range for createMany", async () => {
		const storage = new D1SequenceStorage(createBinding());
		const result = await storage.prepareArgs("Alias", "createMany", {
			data: [{ aliasText: "a" }, { id: 500n, aliasText: "b" }, { aliasText: "c" }],
		});

		expect(result).toEqual({
			data: [
				{ id: 101n, aliasText: "a" },
				{ id: 500n, aliasText: "b" },
				{ id: 102n, aliasText: "c" },
			],
		});
	});

	it("leaves unrelated models and explicit IDs unchanged", async () => {
		const storage = new D1SequenceStorage(createBinding());
		const unrelated = { data: { id: "uuid" } };
		const explicit = { data: { revision: 99n } };

		expect(await storage.prepareArgs("User", "create", unrelated)).toBe(unrelated);
		expect(await storage.prepareArgs("SyncEvent", "create", explicit)).toEqual(explicit);
	});
});
