import { createHash, randomBytes } from "node:crypto";

export const sha256Hex = (value: string): string => {
	return createHash("sha256").update(value).digest("hex");
};

export const randomToken = (bytes = 48): string => {
	return randomBytes(bytes).toString("base64url");
};
