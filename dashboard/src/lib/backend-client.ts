import type { LoginResponse, Session } from "./session";
import { toSession } from "./session";
import i18next from "@/lib/i18n";

export type BackendError = {
  code?: string;
  message?: string;
};

export type BackendRequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean;
  retry?: boolean;
  accessToken?: string;
};

let refreshInFlight:
  | {
      refreshToken: string;
      promise: Promise<Session>;
    }
  | null = null;

function toUserSafeErrorMessage(status: number, payload: unknown): string {
  const unsafeMessage = (payload as BackendError | null)?.message;
  const message = typeof unsafeMessage === "string" ? unsafeMessage.trim() : "";
  const t = i18next.getFixedT(null, "app");

  if (status >= 500) {
    return t("backendErr500");
  }

  if (status === 401) {
    return t("backendErr401");
  }

  if (status === 429) {
    return message || t("backendErr429");
  }

  if ([400, 403, 404, 409, 422].includes(status) && message.length > 0) {
    return message;
  }

  if (status >= 400) {
    return t("backendErrPrefix", { status });
  }

  return t("backendErrDefault");
}

async function refreshSession(backendUrl: string, refreshToken: string): Promise<Session> {
  const normalizedToken = refreshToken.trim();
  if (!normalizedToken) {
    throw new Error("Session expired.");
  }

  if (refreshInFlight && refreshInFlight.refreshToken === normalizedToken) {
    return refreshInFlight.promise;
  }

  const promise = (async () => {
    let refreshed: Response;
    try {
      refreshed = await fetch(`${backendUrl}/v1/auth/refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Maimaid-Client": "web",
        },
        body: JSON.stringify({ refreshToken: normalizedToken }),
      });
    } catch {
      throw new Error(i18next.t("backendErrNet", { ns: "app" }));
    }

    if (!refreshed.ok) {
      throw new Error("Session expired.");
    }

    const refreshPayload = (await refreshed.json()) as LoginResponse;
    return toSession(refreshPayload);
  })();

  refreshInFlight = {
    refreshToken: normalizedToken,
    promise,
  };

  return promise.finally(() => {
    if (refreshInFlight?.promise === promise) {
      refreshInFlight = null;
    }
  });
}

export async function requestJson<T>(
  backendUrl: string,
  path: string,
  session: Session | null,
  setSession: (session: Session) => void,
  clearSession: () => void,
  options?: BackendRequestOptions,
): Promise<T> {
  if (!backendUrl) {
    throw new Error("Missing NEXT_PUBLIC_BACKEND_URL.");
  }

  const auth = options?.auth ?? true;
  const retry = options?.retry ?? true;
  const endpoint = `${backendUrl}/${path.replace(/^\/+/, "")}`;
  const headers = new Headers({
    "Content-Type": "application/json",
    "X-Maimaid-Client": "web",
  });

  const accessToken = options?.accessToken ?? session?.accessToken;
  if (auth && accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: options?.method ?? "GET",
      headers,
      body: options?.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new Error(i18next.t("backendErrNet", { ns: "app" }));
  }

  let payload: unknown = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }

  if (response.status === 401 && auth && retry && session?.refreshToken) {
    try {
      const nextSession = await refreshSession(backendUrl, session.refreshToken);
      setSession(nextSession);
      return requestJson<T>(backendUrl, path, nextSession, setSession, clearSession, { ...options, retry: false });
    } catch {
      clearSession();
      throw new Error(i18next.t("backendErr401", { ns: "app" }));
    }
  }

  if (!response.ok) {
    throw new Error(toUserSafeErrorMessage(response.status, payload));
  }

  return payload as T;
}
