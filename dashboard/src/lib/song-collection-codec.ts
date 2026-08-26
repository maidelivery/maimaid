import { fromBinary } from "@bufbuild/protobuf";
import { SongCollectionShareSchema } from "@/generated/song_collection_pb";
import { BACKEND_URL } from "@/lib/app-helpers";

const PREFIX = "MMD2.";
const MAX_TEXT_LENGTH = 2_000_000;
const MAX_COMPRESSED_BYTES = 1_000_000;
const MAX_RAW_BYTES = 1_000_000;
const MAX_ENTRIES = 10_000;
const COLLECTION_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
const BASE64_URL_PATTERN = /^[A-Za-z0-9_-]+$/u;

export type SharedSongCollectionEntry = {
	songId: string;
	chartType: string;
	difficulty: string;
};

export type SharedSongCollection = {
	name: string;
	entries: SharedSongCollectionEntry[];
};

export type CollectionShareErrorCode = "invalid" | "missing" | "network";

export class CollectionShareError extends Error {
	constructor(readonly code: CollectionShareErrorCode) {
		super(code);
		this.name = "CollectionShareError";
	}
}

function decodeBase64URL(value: string): Uint8Array {
	if (!value || !BASE64_URL_PATTERN.test(value)) {
		throw new CollectionShareError("invalid");
	}

	const padded = value.replaceAll("-", "+").replaceAll("_", "/") + "=".repeat((4 - (value.length % 4)) % 4);
	let binary: string;
	try {
		binary = window.atob(padded);
	} catch {
		throw new CollectionShareError("invalid");
	}

	if (binary.length > MAX_COMPRESSED_BYTES) {
		throw new CollectionShareError("invalid");
	}

	return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function decompressRaw(compressed: Uint8Array): Promise<Uint8Array> {
	if (typeof DecompressionStream === "undefined") {
		throw new CollectionShareError("invalid");
	}

	try {
		const source = new Blob([compressed.slice().buffer]).stream();
		const reader = source.pipeThrough(new DecompressionStream("deflate-raw" as CompressionFormat)).getReader();
		const chunks: Uint8Array[] = [];
		let length = 0;

		while (true) {
			const { done, value } = await reader.read();
			if (done) break;
			length += value.byteLength;
			if (length > MAX_RAW_BYTES) {
				await reader.cancel();
				throw new CollectionShareError("invalid");
			}
			chunks.push(value);
		}

		const raw = new Uint8Array(length);
		let offset = 0;
		for (const chunk of chunks) {
			raw.set(chunk, offset);
			offset += chunk.byteLength;
		}
		return raw;
	} catch (error) {
		if (error instanceof CollectionShareError) throw error;
		throw new CollectionShareError("invalid");
	}
}

function validateCollection(collection: SharedSongCollection): SharedSongCollection {
	if (collection.name.length > 200 || collection.entries.length > MAX_ENTRIES) {
		throw new CollectionShareError("invalid");
	}

	for (const entry of collection.entries) {
		if (
			entry.songId.length === 0 ||
			entry.songId.length > 200 ||
			entry.chartType.length === 0 ||
			entry.chartType.length > 32 ||
			entry.difficulty.length === 0 ||
			entry.difficulty.length > 64
		) {
			throw new CollectionShareError("invalid");
		}
	}

	return collection;
}

export async function decodeCollectionSnapshot(segment: string): Promise<SharedSongCollection> {
	if (!segment.startsWith(PREFIX) || segment.length > MAX_TEXT_LENGTH) {
		throw new CollectionShareError("invalid");
	}

	try {
		const compressed = decodeBase64URL(segment.slice(PREFIX.length));
		const raw = await decompressRaw(compressed);
		const message = fromBinary(SongCollectionShareSchema, raw);
		return validateCollection({
			name: message.name,
			entries: message.entries.map((entry) => ({
				songId: entry.songId,
				chartType: entry.chartType,
				difficulty: entry.difficulty,
			})),
		});
	} catch (error) {
		if (error instanceof CollectionShareError) throw error;
		throw new CollectionShareError("invalid");
	}
}

async function fetchCloudCollection(collectionId: string): Promise<SharedSongCollection> {
	if (!BACKEND_URL) {
		throw new CollectionShareError("network");
	}

	let response: Response;
	try {
		response = await fetch(`${BACKEND_URL}/v1/public/collections/${encodeURIComponent(collectionId)}`);
	} catch {
		throw new CollectionShareError("network");
	}

	if (response.status === 404) {
		throw new CollectionShareError("missing");
	}
	if (!response.ok) {
		throw new CollectionShareError("network");
	}

	try {
		const payload = (await response.json()) as { collection?: SharedSongCollection };
		if (!payload.collection || !Array.isArray(payload.collection.entries)) {
			throw new CollectionShareError("invalid");
		}
		return validateCollection(payload.collection);
	} catch (error) {
		if (error instanceof CollectionShareError) throw error;
		throw new CollectionShareError("invalid");
	}
}

export async function resolveSharedCollection(segment: string): Promise<SharedSongCollection> {
	if (segment.startsWith(PREFIX)) {
		return decodeCollectionSnapshot(segment);
	}
	if (COLLECTION_ID_PATTERN.test(segment)) {
		return fetchCloudCollection(segment);
	}
	throw new CollectionShareError("invalid");
}

export function collectionSegmentFromPathname(pathname: string): string | null {
	const match = /^\/collection\/([^/]+)\/?$/u.exec(pathname);
	if (!match?.[1]) return null;
	try {
		return decodeURIComponent(match[1]);
	} catch {
		return null;
	}
}
