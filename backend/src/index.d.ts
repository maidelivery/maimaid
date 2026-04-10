export {};

declare global {
	interface Uint8ArrayConstructor {
		fromBase64: (
			base64: string,
			options?: {
				alphabet?: "base64" | "base64url";
				lastChunkHandling?: "loose" | "strict" | "stop-before-partial";
			},
		) => Uint8Array<ArrayBuffer>;

		fromHex: (hex: string) => Uint8Array<ArrayBuffer>;
	}

	interface Uint8Array {
		toBase64: (options?: { alphabet?: "base64" | "base64url"; omitPadding?: boolean }) => string;

		toHex: () => string;
	}
}
