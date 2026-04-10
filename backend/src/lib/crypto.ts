const encoder = new TextEncoder();

export const sha256Hex = async (value: string): Promise<string> => {
	const buffer = await crypto.subtle.digest("SHA-256", encoder.encode(value));
	return new Uint8Array(buffer).toHex();
};

export const randomToken = (bytes = 48): string => {
	return crypto.getRandomValues(new Uint8Array(bytes)).toBase64({ alphabet: "base64url" });
};
