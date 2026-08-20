import { describe, expect, it, vi } from "vitest";
import { withD1TransactionCompatibility } from "../.worker/d1-transaction.js";

describe("withD1TransactionCompatibility", () => {
	it("runs an interactive callback against the compatible client", async () => {
		const unsupportedTransaction = vi.fn();
		const client = withD1TransactionCompatibility({
			value: 42,
			$transaction: unsupportedTransaction,
		});

		const result = await client.$transaction(async (transaction) => transaction.value);

		expect(result).toBe(42);
		expect(unsupportedTransaction).not.toHaveBeenCalled();
	});

	it("passes batch transactions to Prisma", async () => {
		const batch = [Promise.resolve(1), Promise.resolve(2)];
		const transaction = vi.fn().mockResolvedValue([1, 2]);
		const client = withD1TransactionCompatibility({ $transaction: transaction });

		expect(await client.$transaction(batch)).toEqual([1, 2]);
		expect(transaction).toHaveBeenCalledWith(batch);
	});
});
