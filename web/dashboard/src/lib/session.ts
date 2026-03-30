export type AuthUser = {
  id: string;
  email: string;
  isAdmin: boolean;
};

export type Session = {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  user: AuthUser;
};

export type LoginResponse = {
  user: AuthUser;
  mfaRequired?: boolean;
  challengeToken?: string;
  methods?: {
    totp: boolean;
    passkey: boolean;
    backupCode: boolean;
  };
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
};

export const LEGACY_SESSION_STORAGE_KEY = "dashboard.session";
export const REFRESH_TOKEN_STORAGE_KEY = "dashboard.refreshToken";

function getSessionStorage(): Storage | null {
  if (typeof globalThis === "undefined") {
    return null;
  }
  if (!("sessionStorage" in globalThis)) {
    return null;
  }
  if (typeof globalThis.sessionStorage?.getItem !== "function") {
    return null;
  }
  return globalThis.sessionStorage;
}

function getLocalStorage(): Storage | null {
  if (typeof globalThis === "undefined") {
    return null;
  }
  if (!("localStorage" in globalThis)) {
    return null;
  }
  if (typeof globalThis.localStorage?.getItem !== "function") {
    return null;
  }
  return globalThis.localStorage;
}

export function toSession(payload: LoginResponse): Session {
  if (!payload.accessToken || !payload.refreshToken || !payload.expiresIn) {
    throw new Error("Invalid login response.");
  }
  return {
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    expiresAt: Date.now() + payload.expiresIn * 1000,
    user: payload.user,
  };
}

export function readStoredRefreshToken(): string | null {
  const sessionStorage = getSessionStorage();
  const localStorage = getLocalStorage();
  if (!sessionStorage && !localStorage) {
    return null;
  }

  const localStoredToken = localStorage?.getItem(REFRESH_TOKEN_STORAGE_KEY)?.trim() ?? "";
  if (localStoredToken) {
    return localStoredToken;
  }

  const sessionStoredToken = sessionStorage?.getItem(REFRESH_TOKEN_STORAGE_KEY)?.trim() ?? "";
  if (sessionStoredToken) {
    try {
      localStorage?.setItem(REFRESH_TOKEN_STORAGE_KEY, sessionStoredToken);
    } catch {
      // noop
    }
    return sessionStoredToken;
  }

  const legacyRaw = localStorage?.getItem(LEGACY_SESSION_STORAGE_KEY);
  if (!legacyRaw) {
    return null;
  }

  try {
    const parsed = JSON.parse(legacyRaw) as Partial<Session>;
    const refreshToken = parsed.refreshToken?.trim() ?? "";
    if (!refreshToken) {
      localStorage?.removeItem(LEGACY_SESSION_STORAGE_KEY);
      return null;
    }

    localStorage?.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken);
    localStorage?.removeItem(LEGACY_SESSION_STORAGE_KEY);
    return refreshToken;
  } catch {
    localStorage?.removeItem(LEGACY_SESSION_STORAGE_KEY);
    return null;
  }
}

export function persistRefreshToken(refreshToken: string) {
  const sessionStorage = getSessionStorage();
  const localStorage = getLocalStorage();
  const normalized = refreshToken.trim();
  if (!normalized) {
    clearStoredSessionArtifacts();
    return;
  }

  try {
    localStorage?.setItem(REFRESH_TOKEN_STORAGE_KEY, normalized);
  } catch {
    // noop
  }

  try {
    sessionStorage?.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  } catch {
    // noop
  }

  try {
    localStorage?.removeItem(LEGACY_SESSION_STORAGE_KEY);
  } catch {
    // noop
  }
}

export function clearStoredSessionArtifacts() {
  const sessionStorage = getSessionStorage();
  const localStorage = getLocalStorage();

  try {
    localStorage?.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  } catch {
    // noop
  }

  try {
    sessionStorage?.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  } catch {
    // noop
  }

  try {
    localStorage?.removeItem(LEGACY_SESSION_STORAGE_KEY);
  } catch {
    // noop
  }
}

export function isSessionExpired(session: Session): boolean {
  if (!Number.isFinite(session.expiresAt)) {
    return true;
  }
  return session.expiresAt <= Date.now();
}

export function parseLegacySessionForMigration(raw: string): Session | null {
  try {
    const parsed = JSON.parse(raw) as Session;
    if (!parsed.accessToken || !parsed.refreshToken || !parsed.user?.id) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}
