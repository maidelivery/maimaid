import * as opaque from "@serenity-kit/opaque";

const KEY_STRETCHING = "memory-constrained" as const;
const PASSWORD_FINGERPRINT_LABEL = "maimaid-password-fingerprint/v1";

const textEncoder = new TextEncoder();

const ensureWebCrypto = (): SubtleCrypto => {
	const subtle = globalThis.crypto?.subtle;
	if (!subtle) {
		throw new Error("Web Crypto is unavailable.");
	}
	return subtle;
};

const toBase64Url = (bytes: Uint8Array): string => {
	let binary = "";
	for (const value of bytes) {
		binary += String.fromCharCode(value);
	}
	return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
};

const sha256Base64Url = async (value: string): Promise<string> => {
	const digest = await ensureWebCrypto().digest("SHA-256", textEncoder.encode(value));
	return toBase64Url(new Uint8Array(digest));
};

const derivePasswordFingerprint = async (exportKey: string): Promise<string> => {
	return sha256Base64Url(`${PASSWORD_FINGERPRINT_LABEL}\u0000${exportKey}`);
};

const ensureOpaqueReady = async (): Promise<void> => {
	await opaque.ready;
};

export type OpaqueRegistrationState = {
	clientRegistrationState: string;
	registrationRequest: string;
};

export type OpaqueLoginState = {
	clientLoginState: string;
	startLoginRequest: string;
};

export const startOpaqueRegistration = async (password: string): Promise<OpaqueRegistrationState> => {
	await ensureOpaqueReady();
	return opaque.client.startRegistration({ password });
};

export const finishOpaqueRegistration = async (input: {
	password: string;
	clientRegistrationState: string;
	registrationResponse: string;
}): Promise<{ registrationRecord: string; passwordFingerprint: string }> => {
	await ensureOpaqueReady();
	const result = opaque.client.finishRegistration({
		password: input.password,
		clientRegistrationState: input.clientRegistrationState,
		registrationResponse: input.registrationResponse,
		keyStretching: KEY_STRETCHING,
	});

	return {
		registrationRecord: result.registrationRecord,
		passwordFingerprint: await derivePasswordFingerprint(result.exportKey),
	};
};

export const startOpaqueLogin = async (password: string): Promise<OpaqueLoginState> => {
	await ensureOpaqueReady();
	return opaque.client.startLogin({ password });
};

export const finishOpaqueLogin = async (input: {
	password: string;
	clientLoginState: string;
	loginResponse: string;
}): Promise<{ finishLoginRequest: string }> => {
	await ensureOpaqueReady();
	const result = opaque.client.finishLogin({
		password: input.password,
		clientLoginState: input.clientLoginState,
		loginResponse: input.loginResponse,
		keyStretching: KEY_STRETCHING,
	});

	if (!result) {
		throw new Error("Invalid email or password.");
	}

	return {
		finishLoginRequest: result.finishLoginRequest,
	};
};
