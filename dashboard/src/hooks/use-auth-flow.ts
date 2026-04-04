import { useTranslation } from "react-i18next";
import { startAuthentication } from "@simplewebauthn/browser";
import { type Dispatch, type SetStateAction, useEffect, useState } from "react";
import {
  isPasswordComplexEnough,
  isValidEmailAddress,
  PASSWORD_COMPLEXITY_HINT,
} from "@/lib/app-helpers";
import type {
  AuthMode,
  ForgotPasswordResponse,
  LoginStep,
  PasskeyLoginStartResponse,
  RegisterResponse,
  ToastSeverity,
  VerificationResult,
} from "@/lib/app-types";
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
  return value.trim().toUpperCase().replace(/[^A-Z0-9]/gu, "");
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
        parsed.protocol === "maimaid:"
        && parsed.hostname === "auth"
        && (parsed.pathname === "/callback" || parsed.pathname === "/callback/")
        && !parsed.search
        && !parsed.hash;

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
    if (
      normalized === "register" ||
      normalized === "forgot" ||
      normalized === "login" ||
      normalized === "reset-password"
    ) {
      return normalized;
    }
    return "login";
  };

  const redirectUri = parseRedirectUri(params.get("redirect_uri") ?? "");
  if (client === "ios" && redirectUri) {
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

  const isAppAuthFlow = appAuthRequest !== null;
  const appAuthRequestedMode = appAuthRequest?.requestedMode ?? null;

  const resetLoginChallenge = () => {
    setMfaChallengeToken("");
    setMfaTotpCode("");
    setMfaBackupCode("");
    setMfaMethods({ totp: false, passkey: false, backupCode: false });
  };

  const resetLoginFlow = () => {
    setLoginStep("email");
    setLoginPassword("");
    resetLoginChallenge();
  };

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
    const payload = await request<{ sessionCode: string }>("v1/auth/session/create", {
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

  const applyLoginPayload = async (payload: LoginResponse, message: string) => {
    const nextSession = toSession(payload);
    setSession(nextSession);
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
    if (!isValidEmailAddress(normalizedEmail) || !loginPassword.trim()) {
      showToast(t("flowMissingEmailPass"), "warning");
      return;
    }

    setLoading(true);
    try {
      const payload = await request<LoginResponse>("v1/auth/login", {
        method: "POST",
        auth: false,
        body: {
          email: normalizedEmail,
          password: loginPassword,
          channel: "web",
        },
      });

      if (payload.mfaRequired) {
        const methods = payload.methods ?? { totp: false, passkey: false, backupCode: false };
        if (!payload.challengeToken || (!methods.totp && !methods.passkey && !methods.backupCode)) {
          throw new Error(t("flowMfaInitFailed"));
        }
        setMfaChallengeToken(payload.challengeToken);
        setMfaMethods(methods);
        setMfaTotpCode("");
        setMfaBackupCode("");
        setLoginStep("mfa");
        if (methods.totp) {
          showToast(t("flowMfaRequireTotp"), "info");
        } else {
          showToast(t("flowMfaRequirePasskey"), "info");
        }
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
      const payload = await request<LoginResponse>("v1/auth/mfa/challenge/totp", {
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
      const options = await request<unknown>("v1/auth/mfa/challenge/passkey/start", {
        method: "POST",
        auth: false,
        body: {
          challengeToken: mfaChallengeToken,
        },
      });
      const browserResponse = await startAuthentication({
        optionsJSON: options as Parameters<typeof startAuthentication>[0]["optionsJSON"],
      });
      const payload = await request<LoginResponse>("v1/auth/mfa/challenge/passkey", {
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
      const payload = await request<LoginResponse>("v1/auth/mfa/challenge/backup-code", {
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
    setLoading(true);
    try {
      const startPayload = await request<PasskeyLoginStartResponse>("v1/auth/passkey/login/start", {
        method: "POST",
        auth: false,
        body: {
          channel: "web",
        },
      });
      const browserResponse = await startAuthentication({
        optionsJSON: startPayload.options as Parameters<typeof startAuthentication>[0]["optionsJSON"],
      });
      const payload = await request<LoginResponse>("v1/auth/passkey/login/finish", {
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
    if (!isValidEmailAddress(normalizedEmail)) {
      showToast(t("flowInvalidEmail"), "warning");
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
      const payload = await request<RegisterResponse>("v1/auth/register", {
        method: "POST",
        auth: false,
        body: {
          email: normalizedEmail,
          password: registerPassword,
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
      const payload = await request<RegisterResponse>("v1/auth/resend-verification", {
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
      await request<{ success: boolean }>("v1/auth/reset-password", {
        method: "POST",
        auth: false,
        body: {
          token,
          newPassword: resetPassword,
        },
      });

      if (isAppAuthFlow && isValidEmailAddress(normalizedResetEmail)) {
        const payload = await request<LoginResponse>("v1/auth/login", {
          method: "POST",
          auth: false,
          body: {
            email: normalizedResetEmail,
            password: resetPassword,
            channel: "web",
          },
        });

        if (payload.mfaRequired) {
          const methods = payload.methods ?? { totp: false, passkey: false, backupCode: false };
          if (!payload.challengeToken || (!methods.totp && !methods.passkey && !methods.backupCode)) {
            throw new Error(t("flowMfaInitFailed"));
          }
          setMfaChallengeToken(payload.challengeToken);
          setMfaMethods(methods);
          setMfaTotpCode("");
          setMfaBackupCode("");
          setLoginEmail(normalizedResetEmail);
          setLoginStep("mfa");
          setAuthMode("login");
          showToast(t("flowResetAppSuccess"), "info");
          return;
        }

        clearPendingForgotEmail();
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
  }, []);

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
  }, [isAppAuthFlow, request, showToast]);

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
