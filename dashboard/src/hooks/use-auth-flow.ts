import { useTranslation } from "react-i18next";
import { startAuthentication } from "@simplewebauthn/browser";
import { type Dispatch, type SetStateAction, useCallback, useEffect, useRef, useState } from "react";
import {
	isPasswordComplexEnough,
	isValidEmailAddress,
	isValidUsername,
	normalizeUsername,
	PASSWORD_COMPLEXITY_HINT,
	USERNAME_HINT,
} from "@/lib/app-helpers";
import type {
	AuthMode,
	ForgotPasswordResponse,
	LoginStep,
	OpaqueLoginStartResponse,
	OpaquePasswordResetStartResponse,
	OpaqueRegistrationStartResponse,
	PasskeyLoginStartResponse,
	RegisterResponse,
	ToastSeverity,
	VerificationResult,
} from "@/lib/app-types";
import {
	finishOpaqueLogin as finishOpaqueClientLogin,
	finishOpaqueRegistration,
	startOpaqueLogin as startOpaqueClientLogin,
	startOpaqueRegistration,
} from "@/lib/opaque-password";
import { toSession, type LoginResponse, type Session } from "@/lib/session";

type RequestOptions = {
	method?: "GET" | "POST" | "PATCH" | "DELETE";
	body?: unknown;
	auth?: boolean;
	retry?: boolean;
	accessToken?: string;
};

type RequestFn = <T>(path: string, options?: RequestOptions) => Promise<T>;

type UseAuthFlowInput = {
	session: Session | null;
	request: RequestFn;
	setSession: Dispatch<SetStateAction<Session | null>>;
	showToast: (message: string, severity?: ToastSeverity) => void;
};

type AppAuthRequest = {
	redirectUri: string;
	requestedMode: "login" | "register" | "forgot" | "reset-password";
};

const APP_AUTH_REDIRECT_URI = "maimaid://auth/callback";
const APP_PENDING_FORGOT_EMAIL_KEY = "dashboard.app.pendingForgotEmail";
const APP_AUTH_REQUEST_KEY = "dashboard.app.authRequest";

function getSessionStorage(): Storage | null {
	if (typeof window === "undefined") {
		return null;
	}
	try {
		return window.sessionStorage;
	} catch {
		return null;
	}
}

function setSessionStorageValue(key: string, value: string) {
	const storage = getSessionStorage();
	if (!storage) {
		return;
	}
	try {
		storage.setItem(key, value);
	} catch {
		// noop
	}
}

function getSessionStorageValue(key: string): string | null {
	const storage = getSessionStorage();
	if (!storage) {
		return null;
	}
	try {
		return storage.getItem(key);
	} catch {
		return null;
	}
}

function removeSessionStorageValue(key: string) {
	const storage = getSessionStorage();
	if (!storage) {
		return;
	}
	try {
		storage.removeItem(key);
	} catch {
		// noop
	}
}

function normalizeBackupCodeInput(value: string) {
	return value
		.trim()
		.toUpperCase()
		.replace(/[^A-Z0-9]/gu, "");
}

function parseAppAuthRequestFromLocation(): AppAuthRequest | null {
	if (typeof window === "undefined") {
		return null;
	}

	const params = new URLSearchParams(window.location.search);
	const client = (params.get("client") ?? "").trim().toLowerCase();
	const authMode = (params.get("authMode") ?? "").trim();

	const parseRedirectUri = (value: string): string | null => {
		const trimmed = value.trim();
		if (!trimmed) {
			return APP_AUTH_REDIRECT_URI;
		}
		try {
			const parsed = new URL(trimmed);
			const isValidRedirect =
				parsed.protocol === "maimaid:" &&
				parsed.hostname === "auth" &&
				(parsed.pathname === "/callback" || parsed.pathname === "/callback/") &&
				!parsed.search &&
				!parsed.hash;

			if (isValidRedirect) {
				return APP_AUTH_REDIRECT_URI;
			}

			return APP_AUTH_REDIRECT_URI;
		} catch {
			return APP_AUTH_REDIRECT_URI;
		}
	};

	const parseRequestedMode = (value: string): AppAuthRequest["requestedMode"] => {
		const normalized = value.trim().toLowerCase();
		if (normalized === "register" || normalized === "forgot" || normalized === "login" || normalized === "reset-password") {
			return normalized;
		}
		return "login";
	};

	const redirectUri = parseRedirectUri(params.get("redirect_uri") ?? "");
	if ((client === "app" || client === "ios") && redirectUri) {
		const parsedRequest: AppAuthRequest = {
			redirectUri,
			requestedMode: parseRequestedMode(authMode),
		};
		setSessionStorageValue(APP_AUTH_REQUEST_KEY, JSON.stringify(parsedRequest));
		return parsedRequest;
	}

	const authAction = (params.get("authAction") ?? "").trim().toLowerCase();
	if (authAction !== "verify-email" && authAction !== "reset-password") {
		return null;
	}

	const rawStored = getSessionStorageValue(APP_AUTH_REQUEST_KEY);
	if (!rawStored) {
		return null;
	}

	try {
		const parsed = JSON.parse(rawStored) as Partial<AppAuthRequest>;
		const storedRedirectUri = parseRedirectUri(typeof parsed.redirectUri === "string" ? parsed.redirectUri : "");
		return {
			redirectUri: storedRedirectUri ?? APP_AUTH_REDIRECT_URI,
			requestedMode: parseRequestedMode(typeof parsed.requestedMode === "string" ? parsed.requestedMode : ""),
		};
	} catch {
		removeSessionStorageValue(APP_AUTH_REQUEST_KEY);
		return null;
	}
}

export function useAuthFlow(input: UseAuthFlowInput) {
	const { t } = useTranslation("auth");
	const { request, setSession, session, showToast } = input;
	const [authMode, setAuthMode] = useState<AuthMode>("login");
	const [loginStep, setLoginStep] = useState<LoginStep>("email");
	const [loginEmail, setLoginEmail] = useState("");
	const [loginPassword, setLoginPassword] = useState("");
	const [registerEmail, setRegisterEmail] = useState("");
	const [registerUsername, setRegisterUsername] = useState("");
	const [registerPassword, setRegisterPassword] = useState("");
	const [registerConfirmPassword, setRegisterConfirmPassword] = useState("");
	const [forgotEmail, setForgotEmail] = useState("");
	const [forgotResultMessage, setForgotResultMessage] = useState<string | null>(null);
	const [verificationEmail, setVerificationEmail] = useState("");
	const [verificationEmailSent, setVerificationEmailSent] = useState<boolean | null>(null);
	const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null);
	const [resetToken, setResetToken] = useState("");
	const [resetEmail, setResetEmail] = useState("");
	const [resetPassword, setResetPassword] = useState("");
	const [resetConfirmPassword, setResetConfirmPassword] = useState("");
	const [resetResultMessage, setResetResultMessage] = useState<string | null>(null);
	const [loading, setLoading] = useState(false);
	const [mfaChallengeToken, setMfaChallengeToken] = useState("");
	const [mfaMethods, setMfaMethods] = useState<{ totp: boolean; passkey: boolean; backupCode: boolean }>({
		totp: false,
		passkey: false,
		backupCode: false,
	});
	const [mfaTotpCode, setMfaTotpCode] = useState("");
	const [mfaBackupCode, setMfaBackupCode] = useState("");
	const [appAuthRequest, setAppAuthRequest] = useState<AppAuthRequest | null>(null);
	const legacyPasswordForUpgradeRef = useRef<string | null>(null);

	const isAppAuthFlow = appAuthRequest !== null;
	const appAuthRequestedMode = appAuthRequest?.requestedMode ?? null;

	const clearPendingLegacyPasswordUpgrade = useCallback(() => {
		legacyPasswordForUpgradeRef.current = null;
	}, []);

	const resetLoginChallenge = useCallback(() => {
		setMfaChallengeToken("");
		setMfaTotpCode("");
		setMfaBackupCode("");
		setMfaMethods({ totp: false, passkey: false, backupCode: false });
	}, []);

	const resetLoginFlow = useCallback(() => {
		setLoginStep("email");
		setLoginPassword("");
		clearPendingLegacyPasswordUpgrade();
		resetLoginChallenge();
	}, [clearPendingLegacyPasswordUpgrade, resetLoginChallenge]);

	const savePendingForgotEmail = (email: string) => {
		setSessionStorageValue(APP_PENDING_FORGOT_EMAIL_KEY, email.trim().toLowerCase());
	};

	const readPendingForgotEmail = (): string => {
		return (getSessionStorageValue(APP_PENDING_FORGOT_EMAIL_KEY) ?? "").trim().toLowerCase();
	};

	const clearPendingForgotEmail = () => {
		removeSessionStorageValue(APP_PENDING_FORGOT_EMAIL_KEY);
	};

	const createAppSessionCode = async (accessToken: string): Promise<string> => {
		const payload = await request<{ sessionCode: string }>("v1/auth/session:create", {
			method: "POST",
			auth: true,
			retry: false,
			accessToken,
		});
		const sessionCode = payload.sessionCode?.trim() ?? "";
		if (!sessionCode) {
			throw new Error(t("flowSessionCreateFailed"));
		}
		return sessionCode;
	};

	const redirectToAppWithSession = async (payload: LoginResponse): Promise<boolean> => {
		if (!appAuthRequest) {
			return false;
		}

		if (!payload.accessToken) {
			return false;
		}

		try {
			const sessionCode = await createAppSessionCode(payload.accessToken);
			const callback = new URL(appAuthRequest.redirectUri);
			callback.searchParams.set("type", "session");
			callback.searchParams.set("result", "success");
			callback.searchParams.set("sessionCode", sessionCode);

			removeSessionStorageValue(APP_AUTH_REQUEST_KEY);
			window.location.assign(callback.toString());
			return true;
		} catch (error) {
			showToast((error as Error).message, "error");
			return false;
		}
	};

	const silentlyUpgradeLegacyPassword = async (accessToken: string): Promise<void> => {
		const password = legacyPasswordForUpgradeRef.current;
		if (!password) {
			return;
		}

		legacyPasswordForUpgradeRef.current = null;

		try {
			const registrationState = await startOpaqueRegistration(password);
			const startPayload = await request<OpaqueRegistrationStartResponse>("v1/auth/password:enrollOpaque:start", {
				method: "POST",
				body: {
					registrationRequest: registrationState.registrationRequest,
				},
				accessToken,
			});
			const finishPayload = await finishOpaqueRegistration({
				password,
				clientRegistrationState: registrationState.clientRegistrationState,
				registrationResponse: startPayload.registrationResponse,
			});
			await request<{ success: boolean }>("v1/auth/password:enrollOpaque:finish", {
				method: "POST",
				body: {
					registrationRecord: finishPayload.registrationRecord,
					passwordFingerprint: finishPayload.passwordFingerprint,
				},
				accessToken,
			});
		} catch {
			// Keep the current login session even if the silent legacy upgrade fails.
		}
	};

	const transitionToMfaStep = (payload: LoginResponse, message: string, email?: string) => {
		const methods = payload.methods ?? { totp: false, passkey: false, backupCode: false };
		if (!payload.challengeToken || (!methods.totp && !methods.passkey && !methods.backupCode)) {
			throw new Error(t("flowMfaInitFailed"));
		}

		if (email) {
			setLoginEmail(email);
		}
		setMfaChallengeToken(payload.challengeToken);
		setMfaMethods(methods);
		setMfaTotpCode("");
		setMfaBackupCode("");
		setLoginStep("mfa");
		setAuthMode("login");
		showToast(message, "info");
	};

	const loginWithPasswordProtocol = async (email: string, password: string): Promise<LoginResponse> => {
		clearPendingLegacyPasswordUpgrade();

		const clientStart = await startOpaqueClientLogin(password);
		const startPayload = await request<OpaqueLoginStartResponse>("v1/auth/login:start", {
			method: "POST",
			auth: false,
			body: {
				email,
				startLoginRequest: clientStart.startLoginRequest,
			},
		});

		if (startPayload.protocol === "legacy-bcrypt") {
			const payload = await request<LoginResponse>("v1/auth/login", {
				method: "POST",
				auth: false,
				body: {
					email,
					password,
					channel: "web",
				},
			});
			legacyPasswordForUpgradeRef.current = password;
			return payload;
		}

		const finishPayload = await finishOpaqueClientLogin({
			password,
			clientLoginState: clientStart.clientLoginState,
			loginResponse: startPayload.loginResponse,
		});

		return request<LoginResponse>("v1/auth/login:finish", {
			method: "POST",
			auth: false,
			body: {
				challengeToken: startPayload.challengeToken,
				finishLoginRequest: finishPayload.finishLoginRequest,
			},
		});
	};

	const applyLoginPayload = async (payload: LoginResponse, message: string) => {
		const nextSession = toSession(payload);
		setSession(nextSession);
		await silentlyUpgradeLegacyPassword(nextSession.accessToken);
		setAuthMode("login");
		resetLoginFlow();

		if (await redirectToAppWithSession(payload)) {
			return;
		}

		showToast(message, "success");
	};

	const completeAppLoginWithExistingSession = async () => {
		if (!session || !appAuthRequest) {
			return;
		}

		clearPendingLegacyPasswordUpgrade();
		setLoading(true);
		try {
			const payload = await request<LoginResponse>("v1/auth/refresh", {
				method: "POST",
				auth: false,
				body: {
					refreshToken: session.refreshToken,
				},
			});
			await applyLoginPayload(payload, t("flowLoginAppSuccess"));
			return;
		} catch {
			const expiresIn = Math.max(1, Math.floor((session.expiresAt - Date.now()) / 1000));
			const fallbackPayload: LoginResponse = {
				user: session.user,
				accessToken: session.accessToken,
				refreshToken: session.refreshToken,
				expiresIn,
			};

			if (await redirectToAppWithSession(fallbackPayload)) {
				return;
			}

			showToast(t("flowSessionExpired"), "warning");
			setSession(null);
		} finally {
			setLoading(false);
		}
	};

	const handleLoginContinue = async () => {
		const normalizedEmail = loginEmail.trim().toLowerCase();
		if (!isValidEmailAddress(normalizedEmail)) {
			showToast(t("flowInvalidEmail"), "warning");
			return;
		}
		setLoginEmail(normalizedEmail);
		setLoginStep("password");
	};

	const handleLoginWithPassword = async () => {
		const normalizedEmail = loginEmail.trim().toLowerCase();
		const password = loginPassword;
		if (!isValidEmailAddress(normalizedEmail) || !loginPassword.trim()) {
			showToast(t("flowMissingEmailPass"), "warning");
			return;
		}

		setLoading(true);
		try {
			const payload = await loginWithPasswordProtocol(normalizedEmail, password);

			if (payload.mfaRequired) {
				const methods = payload.methods ?? { totp: false, passkey: false, backupCode: false };
				transitionToMfaStep(payload, methods.totp ? t("flowMfaRequireTotp") : t("flowMfaRequirePasskey"));
				return;
			}

			await applyLoginPayload(payload, t("flowLoginSuccess"));
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleTotpChallenge = async () => {
		if (!mfaChallengeToken || mfaTotpCode.trim().length !== 6) {
			showToast(t("flowTotpLengthHint"), "warning");
			return;
		}
		setLoading(true);
		try {
			const payload = await request<LoginResponse>("v1/auth/mfa/challenges:verifyTotp", {
				method: "POST",
				auth: false,
				body: {
					challengeToken: mfaChallengeToken,
					code: mfaTotpCode.trim(),
				},
			});
			await applyLoginPayload(payload, t("flowTotpSuccess"));
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handlePasskeyChallenge = async () => {
		if (!mfaChallengeToken) {
			showToast(t("flowPasskeyMissingChallenge"), "error");
			return;
		}
		setLoading(true);
		try {
			const options = await request<unknown>("v1/auth/mfa/challenges:startPasskeyLogin", {
				method: "POST",
				auth: false,
				body: {
					challengeToken: mfaChallengeToken,
				},
			});
			const browserResponse = await startAuthentication({
				optionsJSON: options as Parameters<typeof startAuthentication>[0]["optionsJSON"],
			});
			const payload = await request<LoginResponse>("v1/auth/mfa/challenges:verifyPasskey", {
				method: "POST",
				auth: false,
				body: {
					challengeToken: mfaChallengeToken,
					response: browserResponse,
				},
			});
			await applyLoginPayload(payload, t("flowPasskeySuccess"));
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleBackupCodeChallenge = async () => {
		const normalizedCode = normalizeBackupCodeInput(mfaBackupCode);
		if (!mfaChallengeToken || normalizedCode.length !== 8) {
			showToast(t("flowBackupLengthHint"), "warning");
			return;
		}
		setLoading(true);
		try {
			const payload = await request<LoginResponse>("v1/auth/mfa/challenges:verifyBackupCode", {
				method: "POST",
				auth: false,
				body: {
					challengeToken: mfaChallengeToken,
					code: mfaBackupCode.trim(),
				},
			});
			await applyLoginPayload(payload, t("flowBackupSuccess"));
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleDirectPasskeyLogin = async () => {
		clearPendingLegacyPasswordUpgrade();
		setLoading(true);
		try {
			const startPayload = await request<PasskeyLoginStartResponse>("v1/auth/passkeys:startLogin", {
				method: "POST",
				auth: false,
				body: {
					channel: "web",
				},
			});
			const browserResponse = await startAuthentication({
				optionsJSON: startPayload.options as Parameters<typeof startAuthentication>[0]["optionsJSON"],
			});
			const payload = await request<LoginResponse>("v1/auth/passkeys:finishLogin", {
				method: "POST",
				auth: false,
				body: {
					challengeToken: startPayload.challengeToken,
					response: browserResponse,
				},
			});
			await applyLoginPayload(payload, t("flowPasskeyLoginSuccess"));
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleRegister = async () => {
		const normalizedEmail = registerEmail.trim().toLowerCase();
		const normalizedUsername = normalizeUsername(registerUsername);
		const password = registerPassword;
		if (!isValidEmailAddress(normalizedEmail)) {
			showToast(t("flowInvalidEmail"), "warning");
			return;
		}
		if (!isValidUsername(normalizedUsername)) {
			showToast(t("flowInvalidUsername") || USERNAME_HINT, "warning");
			return;
		}
		if (!isPasswordComplexEnough(registerPassword)) {
			showToast(PASSWORD_COMPLEXITY_HINT, "warning");
			return;
		}
		if (registerPassword !== registerConfirmPassword) {
			showToast(t("flowRegisterPwdMismatch"), "warning");
			return;
		}

		setLoading(true);
		try {
			const registrationState = await startOpaqueRegistration(password);
			const startPayload = await request<OpaqueRegistrationStartResponse>("v1/auth/register:start", {
				method: "POST",
				auth: false,
				body: {
					email: normalizedEmail,
					registrationRequest: registrationState.registrationRequest,
				},
			});
			const finishPayload = await finishOpaqueRegistration({
				password,
				clientRegistrationState: registrationState.clientRegistrationState,
				registrationResponse: startPayload.registrationResponse,
			});
			const payload = await request<RegisterResponse>("v1/auth/register:finish", {
				method: "POST",
				auth: false,
				body: {
					email: normalizedEmail,
					username: normalizedUsername,
					registrationRecord: finishPayload.registrationRecord,
					passwordFingerprint: finishPayload.passwordFingerprint,
					...(isAppAuthFlow && appAuthRequest
						? {
								channel: "app",
								redirectUri: appAuthRequest.redirectUri,
							}
						: {}),
				},
			});
			setVerificationEmail(normalizedEmail);
			setVerificationEmailSent(payload.verificationEmailSent);
			setVerificationResult(null);
			setRegisterUsername(normalizedUsername);
			setRegisterPassword("");
			setRegisterConfirmPassword("");
			setAuthMode("verify-email");

			if (payload.verificationEmailSent) {
				showToast(t("flowRegisterSuccess"), "success");
			} else {
				showToast(t("flowRegisterEmailFailed"), "warning");
			}
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleResendVerification = async () => {
		const targetEmail = verificationEmail.trim().toLowerCase();
		if (!isValidEmailAddress(targetEmail)) {
			showToast(t("flowResendMissingEmail"), "warning");
			return;
		}

		setLoading(true);
		try {
			const payload = await request<RegisterResponse>("v1/auth/verification:resend", {
				method: "POST",
				auth: false,
				body: {
					email: targetEmail,
					...(isAppAuthFlow && appAuthRequest
						? {
								channel: "app",
								redirectUri: appAuthRequest.redirectUri,
							}
						: {}),
				},
			});
			setVerificationEmailSent(payload.verificationEmailSent);
			if (payload.verificationEmailSent) {
				showToast(t("flowResendSuccess"), "success");
			} else {
				showToast(t("flowResendFailed"), "warning");
			}
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleForgotPassword = async () => {
		const normalizedEmail = forgotEmail.trim().toLowerCase();
		if (!isValidEmailAddress(normalizedEmail)) {
			showToast(t("flowInvalidEmail"), "warning");
			return;
		}

		setLoading(true);
		try {
			const payload = await request<ForgotPasswordResponse>("v1/auth/forgot-password", {
				method: "POST",
				auth: false,
				body: {
					email: normalizedEmail,
					...(isAppAuthFlow && appAuthRequest
						? {
								channel: "app",
								redirectUri: appAuthRequest.redirectUri,
							}
						: {}),
				},
			});
			setResetEmail(normalizedEmail);
			savePendingForgotEmail(normalizedEmail);
			if (payload.resetEmailSent === false) {
				setForgotResultMessage(t("flowForgotFailed"));
				showToast(t("flowForgotFailed"), "warning");
			} else {
				setForgotResultMessage(t("flowForgotInboxHint"));
				showToast(t("flowForgotSuccess"), "success");
			}
		} catch (error) {
			showToast((error as Error).message, "error");
		} finally {
			setLoading(false);
		}
	};

	const handleResetPassword = async () => {
		const token = resetToken.trim();
		const normalizedResetEmail = resetEmail.trim().toLowerCase();
		const password = resetPassword;

		if (token.length < 20) {
			showToast(t("flowResetInvalid"), "warning");
			setAuthMode("forgot");
			setForgotResultMessage(t("flowResetInvalid"));
			return;
		}
		if (!isPasswordComplexEnough(resetPassword)) {
			showToast(PASSWORD_COMPLEXITY_HINT, "warning");
			return;
		}
		if (resetPassword !== resetConfirmPassword) {
			showToast(t("flowRegisterPwdMismatch"), "warning");
			return;
		}

		setLoading(true);
		try {
			const registrationState = await startOpaqueRegistration(password);
			const startPayload = await request<OpaquePasswordResetStartResponse>("v1/auth/reset-password:start", {
				method: "POST",
				auth: false,
				body: {
					token,
					registrationRequest: registrationState.registrationRequest,
				},
			});
			const finishPayload = await finishOpaqueRegistration({
				password,
				clientRegistrationState: registrationState.clientRegistrationState,
				registrationResponse: startPayload.registrationResponse,
			});
			await request<{ success: boolean }>("v1/auth/reset-password:finish", {
				method: "POST",
				auth: false,
				body: {
					token,
					registrationRecord: finishPayload.registrationRecord,
					passwordFingerprint: finishPayload.passwordFingerprint,
				},
			});

			const resolvedResetEmail = startPayload.email.trim().toLowerCase();
			if (isValidEmailAddress(resolvedResetEmail)) {
				setResetEmail(resolvedResetEmail);
			}
			clearPendingForgotEmail();

			if (isAppAuthFlow && isValidEmailAddress(resolvedResetEmail || normalizedResetEmail)) {
				const loginEmailAddress = resolvedResetEmail || normalizedResetEmail;
				const payload = await loginWithPasswordProtocol(loginEmailAddress, password);

				if (payload.mfaRequired) {
					transitionToMfaStep(payload, t("flowResetAppSuccess"), loginEmailAddress);
					return;
				}

				setResetResultMessage(null);
				setResetPassword("");
				setResetConfirmPassword("");
				await applyLoginPayload(payload, t("flowResetLoginSuccess"));
				return;
			}

			setResetResultMessage(t("flowResetLoginHint"));
			setResetPassword("");
			setResetConfirmPassword("");

			if (isAppAuthFlow) {
				showToast(t("flowResetAppLoginHint"), "warning");
			} else {
				showToast(t("flowResetLoginHint"), "success");
			}
		} catch (error) {
			const message = (error as Error).message;
			if (message.toLowerCase().includes("different from your current password")) {
				showToast(t("flowResetSamePwd"), "warning");
			} else {
				showToast(message, "error");
			}
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		const parsed = parseAppAuthRequestFromLocation();
		if (!parsed) {
			return;
		}

		setAppAuthRequest(parsed);
		setVerificationResult(null);

		if (parsed.requestedMode === "register") {
			setAuthMode("register");
			return;
		}

		if (parsed.requestedMode === "forgot") {
			setAuthMode("forgot");
			const rememberedEmail = readPendingForgotEmail();
			if (rememberedEmail) {
				setForgotEmail(rememberedEmail);
				setResetEmail(rememberedEmail);
			}
			setForgotResultMessage(null);
			return;
		}

		if (parsed.requestedMode === "reset-password") {
			const params = new URLSearchParams(window.location.search);
			const token = (params.get("token") ?? "").trim();
			const email = (params.get("email") ?? "").trim().toLowerCase();

			if (token.length >= 20) {
				setAuthMode("reset-password");
				setResetToken(token);
				setResetPassword("");
				setResetConfirmPassword("");
				setResetResultMessage(null);
				setForgotResultMessage(null);

				if (isValidEmailAddress(email)) {
					setResetEmail(email);
					savePendingForgotEmail(email);
				} else {
					const rememberedEmail = readPendingForgotEmail();
					if (rememberedEmail) {
						setResetEmail(rememberedEmail);
					}
				}

				return;
			}

			setAuthMode("forgot");
			setResetToken("");
			setForgotResultMessage(t("flowResetInvalid"));
			return;
		}

		setAuthMode("login");
		resetLoginFlow();
	}, [resetLoginFlow, t]);

	useEffect(() => {
		const params = new URLSearchParams(window.location.search);
		const authAction = params.get("authAction");
		const status = params.get("status");
		const code = params.get("code") ?? "";
		const token = params.get("token") ?? "";
		const email = (params.get("email") ?? "").trim().toLowerCase();

		if (authAction !== "verify-email" && authAction !== "reset-password") {
			return;
		}

		void (async () => {
			if (authAction === "verify-email") {
				if (status === "success") {
					setVerificationResult({ status: "success", code: code || "email_verified" });
					if (isAppAuthFlow) {
						showToast(t("flowVerifySuccessApp"), "success");
					} else {
						showToast(t("flowVerifySuccess"), "success");
					}
				} else {
					setVerificationResult({ status: "error", code: code || "invalid_verification_token" });
					showToast(t("flowVerifyInvalid"), "warning");
				}

				setAuthMode("verify-email");
			} else if (status === "success" && token.trim().length >= 20) {
				setAuthMode("reset-password");
				setResetToken(token.trim());
				setResetPassword("");
				setResetConfirmPassword("");
				setResetResultMessage(null);
				setForgotResultMessage(null);

				if (isValidEmailAddress(email)) {
					setResetEmail(email);
					savePendingForgotEmail(email);
				} else {
					const rememberedEmail = readPendingForgotEmail();
					if (rememberedEmail) {
						setResetEmail(rememberedEmail);
					}
				}
			} else {
				setAuthMode("forgot");
				setResetToken("");
				setForgotResultMessage(t("flowResetInvalid"));
				showToast("重置链接无效或已过期。", "warning");
			}

			const nextUrl = new URL(window.location.href);
			nextUrl.searchParams.delete("authAction");
			nextUrl.searchParams.delete("status");
			nextUrl.searchParams.delete("code");
			nextUrl.searchParams.delete("token");
			nextUrl.searchParams.delete("email");
			window.history.replaceState({}, "", nextUrl.toString());
		})();
	}, [isAppAuthFlow, request, showToast, t]);

	return {
		authMode,
		setAuthMode,
		loginStep,
		loginEmail,
		setLoginEmail,
		loginPassword,
		setLoginPassword,
		registerEmail,
		setRegisterEmail,
		registerUsername,
		setRegisterUsername,
		registerPassword,
		setRegisterPassword,
		registerConfirmPassword,
		setRegisterConfirmPassword,
		forgotEmail,
		setForgotEmail,
		forgotResultMessage,
		setForgotResultMessage,
		verificationEmail,
		verificationEmailSent,
		verificationResult,
		setVerificationResult,
		resetEmail,
		setResetEmail,
		resetPassword,
		setResetPassword,
		resetConfirmPassword,
		setResetConfirmPassword,
		resetResultMessage,
		setResetResultMessage,
		setResetToken,
		loading,
		mfaMethods,
		mfaTotpCode,
		setMfaTotpCode,
		mfaBackupCode,
		setMfaBackupCode,
		isAppAuthFlow,
		appAuthRequestedMode,
		completeAppLoginWithExistingSession,
		resetLoginFlow,
		handleLoginContinue,
		handleLoginWithPassword,
		handleTotpChallenge,
		handlePasskeyChallenge,
		handleBackupCodeChallenge,
		handleDirectPasskeyLogin,
		handleRegister,
		handleResendVerification,
		handleForgotPassword,
		handleResetPassword,
	};
}
