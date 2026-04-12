import * as opaque from "@serenity-kit/opaque";
import { AppError } from "./errors.js";
import { sha256Hex } from "./crypto.js";

export const PASSWORD_FINGERPRINT_LABEL = "maimaid-password-fingerprint/v1";

const normalizeOpaqueString = (value: string, code: string, message: string): string => {
	const normalized = value.trim();
	if (!normalized || normalized.includes("__proto__")) {
		throw new AppError(400, code, message);
	}
	return normalized;
};

const withOpaqueReady = async <T>(fn: () => T): Promise<T> => {
	await opaque.ready;
	return fn();
};

export const normalizeOpaqueEnvelope = (value: string): string =>
	normalizeOpaqueString(value, "invalid_opaque_payload", "Opaque payload is invalid.");

export const normalizePasswordFingerprint = (value: string): string =>
	normalizeOpaqueString(value, "invalid_password_fingerprint", "Password fingerprint is invalid.");

export const hashPasswordFingerprint = async (value: string): Promise<string> => {
	return sha256Hex(normalizePasswordFingerprint(value));
};

export const createOpaqueRegistrationResponse = async (input: {
	serverSetup: string;
	userIdentifier: string;
	registrationRequest: string;
}): Promise<string> => {
	const registrationRequest = normalizeOpaqueEnvelope(input.registrationRequest);
	try {
		return await withOpaqueReady(
			() =>
				opaque.server.createRegistrationResponse({
					serverSetup: input.serverSetup,
					userIdentifier: input.userIdentifier,
					registrationRequest,
				}).registrationResponse,
		);
	} catch {
		throw new AppError(400, "invalid_opaque_payload", "Opaque payload is invalid.");
	}
};

export const startOpaqueLogin = async (input: {
	serverSetup: string;
	userIdentifier: string;
	registrationRecord: string;
	startLoginRequest: string;
}): Promise<{ serverLoginState: string; loginResponse: string }> => {
	const startLoginRequest = normalizeOpaqueEnvelope(input.startLoginRequest);
	const registrationRecord = normalizeOpaqueEnvelope(input.registrationRecord);
	try {
		return await withOpaqueReady(() =>
			opaque.server.startLogin({
				serverSetup: input.serverSetup,
				userIdentifier: input.userIdentifier,
				registrationRecord,
				startLoginRequest,
			}),
		);
	} catch {
		throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
	}
};

export const finishOpaqueLogin = async (input: {
	serverLoginState: string;
	finishLoginRequest: string;
}): Promise<void> => {
	const finishLoginRequest = normalizeOpaqueEnvelope(input.finishLoginRequest);
	const serverLoginState = normalizeOpaqueEnvelope(input.serverLoginState);
	try {
		await withOpaqueReady(() =>
			opaque.server.finishLogin({
				serverLoginState,
				finishLoginRequest,
			}),
		);
	} catch {
		throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
	}
};
