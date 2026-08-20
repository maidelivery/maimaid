type D1SequenceStatement = {
	bind(...values: unknown[]): D1SequenceStatement;
	first<T>(): Promise<T | null>;
};

export type D1SequenceBinding = {
	prepare(query: string): D1SequenceStatement;
};

const sequenceFields = new Map<string, string>([
	["CatalogSnapshot", "id"],
	["Sheet", "id"],
	["Alias", "id"],
	["SyncEvent", "revision"],
	["StaticBundle", "id"],
	["ChartFitSnapshot", "id"],
	["JobQueue", "id"],
]);

const isRecord = (value: unknown): value is Record<string, unknown> =>
	typeof value === "object" && value !== null && !Array.isArray(value);

export class D1SequenceStorage {
	constructor(private readonly binding: D1SequenceBinding) {}

	async prepareArgs(model: string, operation: string, args: unknown): Promise<unknown> {
		const field = sequenceFields.get(model);
		if (!field || !isRecord(args)) return args;

		if (operation === "upsert") {
			return { ...args, create: await this.prepareData(model, field, args.create) };
		}
		if (!["create", "createMany", "createManyAndReturn"].includes(operation)) return args;
		if (Array.isArray(args.data)) {
			const missingCount = args.data.filter((data) => isRecord(data) && data[field] == null).length;
			if (missingCount === 0) return args;
			const firstValue = await this.reserve(model, missingCount);
			let nextValue = firstValue;
			return {
				...args,
				data: args.data.map((data) => {
					if (!isRecord(data) || data[field] != null) return data;
					const prepared = { ...data, [field]: nextValue };
					nextValue += 1n;
					return prepared;
				}),
			};
		}
		return { ...args, data: await this.prepareData(model, field, args.data) };
	}

	private async prepareData(model: string, field: string, data: unknown): Promise<unknown> {
		if (!isRecord(data) || data[field] != null) return data;
		return { ...data, [field]: await this.reserve(model, 1) };
	}

	private async reserve(model: string, count: number): Promise<bigint> {
		const row = await this.binding
			.prepare('UPDATE "d1_sequences" SET "value" = "value" + ? WHERE "name" = ? RETURNING "value"')
			.bind(count, model)
			.first<{ value: number }>();
		if (!row) {
			throw new Error(`D1 sequence is missing: ${model}`);
		}
		return BigInt(row.value) - BigInt(count) + 1n;
	}
}
