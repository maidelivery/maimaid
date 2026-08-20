const EXTERNAL_JSON_BYTES = 50_000;

type R2ObjectBody = {
	text(): Promise<string>;
};

export type R2JsonBucket = {
	get(key: string): Promise<R2ObjectBody | null>;
	put(
		key: string,
		value: string,
		options?: {
			httpMetadata?: { contentType: string };
			customMetadata?: Record<string, string>;
		},
	): Promise<unknown>;
};

type R2JsonReference = {
	$r2: string;
	sha256: string;
	bytes: number;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
	typeof value === "object" && value !== null && !Array.isArray(value);

const asReference = (value: unknown): R2JsonReference | null => {
	if (!isRecord(value)) return null;
	if (typeof value.$r2 !== "string" || typeof value.sha256 !== "string" || typeof value.bytes !== "number") {
		return null;
	}
	return {
		$r2: value.$r2,
		sha256: value.sha256,
		bytes: value.bytes,
	};
};

const sha256Hex = async (value: Uint8Array): Promise<string> => {
	const digest = await crypto.subtle.digest("SHA-256", value);
	return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
};

export class R2JsonStorage {
	private readonly cache = new Map<string, Promise<unknown>>();

	constructor(private readonly bucket: R2JsonBucket) {}

	async externalize(model: string, value: unknown): Promise<unknown> {
		if (asReference(value)) return value;

		const serialized = JSON.stringify(value);
		const bytes = new TextEncoder().encode(serialized);
		if (bytes.byteLength <= EXTERNAL_JSON_BYTES) return value;

		const sha256 = await sha256Hex(bytes);
		const objectKey = `worker-json/${model}/${Date.now()}-${crypto.randomUUID()}.json`;
		await this.bucket.put(objectKey, serialized, {
			httpMetadata: { contentType: "application/json" },
			customMetadata: {
				sha256,
				bytes: String(bytes.byteLength),
			},
		});
		this.cache.set(objectKey, Promise.resolve(value));
		return { $r2: objectKey, sha256, bytes: bytes.byteLength } satisfies R2JsonReference;
	}

	async hydrate(value: unknown): Promise<unknown> {
		const reference = asReference(value);
		if (!reference) return value;

		const cached = this.cache.get(reference.$r2);
		if (cached) return cached;

		const pending = this.load(reference);
		this.cache.set(reference.$r2, pending);
		try {
			return await pending;
		} catch (error) {
			this.cache.delete(reference.$r2);
			throw error;
		}
	}

	async prepareArgs(model: string, operation: string, args: unknown): Promise<unknown> {
		if (!isRecord(args)) return args;

		if (operation === "upsert") {
			return {
				...args,
				create: await this.prepareData(model, args.create),
				update: await this.prepareData(model, args.update),
			};
		}
		if (!["create", "createMany", "createManyAndReturn", "update", "updateMany", "updateManyAndReturn"].includes(operation)) {
			return args;
		}
		if (Array.isArray(args.data)) {
			return {
				...args,
				data: await Promise.all(args.data.map((data) => this.prepareData(model, data))),
			};
		}
		return { ...args, data: await this.prepareData(model, args.data) };
	}

	async hydrateResult(result: unknown): Promise<unknown> {
		if (Array.isArray(result)) {
			return Promise.all(result.map((row) => this.hydrateResult(row)));
		}
		if (!isRecord(result) || !("payloadJson" in result)) return result;
		return { ...result, payloadJson: await this.hydrate(result.payloadJson) };
	}

	private async prepareData(model: string, data: unknown): Promise<unknown> {
		if (!isRecord(data) || !("payloadJson" in data)) return data;
		const fieldValue = data.payloadJson;
		if (isRecord(fieldValue) && "set" in fieldValue) {
			return {
				...data,
				payloadJson: { ...fieldValue, set: await this.externalize(model, fieldValue.set) },
			};
		}
		return { ...data, payloadJson: await this.externalize(model, fieldValue) };
	}

	private async load(reference: R2JsonReference): Promise<unknown> {
		const object = await this.bucket.get(reference.$r2);
		if (!object) {
			throw new Error(`R2 JSON object is missing: ${reference.$r2}`);
		}
		const serialized = await object.text();
		const bytes = new TextEncoder().encode(serialized);
		if (bytes.byteLength !== reference.bytes) {
			throw new Error(`R2 JSON object size mismatch: ${reference.$r2}`);
		}
		const sha256 = await sha256Hex(bytes);
		if (sha256 !== reference.sha256) {
			throw new Error(`R2 JSON object checksum mismatch: ${reference.$r2}`);
		}
		return JSON.parse(serialized) as unknown;
	}
}
