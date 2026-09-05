import i18n from "@/i18n";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

const EXPIRES_KEY = "docuforge.accessExpiresAt";

/** Refresh access token this many ms before expiry. */
export const ACCESS_SKEW_MS = 60_000;

export type PersistedTokenResponse = {
  accessToken: string;
  refreshToken?: string;
  expiresIn: number;
};

function acceptLanguage(): string {
  return i18n.language?.toLowerCase().startsWith("pt") ? "pt" : "fr";
}

export type SessionTokens = {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
};

type Listener = () => void;

const listeners = new Set<Listener>();
let refreshInFlight: Promise<SessionTokens | null> | null = null;
/** In-memory access token — refresh lives in httpOnly cookie. */
let accessTokenMemory: string | null = null;

export function subscribeSession(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify() {
  listeners.forEach((listener) => listener());
}

export function getAccessToken(): string | null {
  return accessTokenMemory;
}

export function getAccessExpiresAt(): number | null {
  const raw = sessionStorage.getItem(EXPIRES_KEY);
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

export function getSession(): SessionTokens | null {
  const accessToken = getAccessToken();
  const expiresAt = getAccessExpiresAt();
  if (!accessToken || expiresAt == null) return null;
  return { accessToken, refreshToken: "", expiresAt };
}

export function setSession(tokens: PersistedTokenResponse): SessionTokens {
  const expiresAt = Date.now() + Math.max(1, tokens.expiresIn) * 1000;
  accessTokenMemory = tokens.accessToken;
  sessionStorage.setItem(EXPIRES_KEY, String(expiresAt));
  // Clear legacy localStorage tokens from pre-U1 sessions.
  localStorage.removeItem("docuforge.accessToken");
  localStorage.removeItem("docuforge.refreshToken");
  localStorage.removeItem("docuforge.accessExpiresAt");
  notify();
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken ?? "",
    expiresAt,
  };
}

export function clearSession(): void {
  accessTokenMemory = null;
  sessionStorage.removeItem(EXPIRES_KEY);
  localStorage.removeItem("docuforge.accessToken");
  localStorage.removeItem("docuforge.refreshToken");
  localStorage.removeItem("docuforge.accessExpiresAt");
  notify();
}

export function accessNeedsRefresh(now = Date.now()): boolean {
  const expiresAt = getAccessExpiresAt();
  if (expiresAt == null) return !!getAccessToken();
  return expiresAt - now <= ACCESS_SKEW_MS;
}

export async function ensureFreshAccessToken(): Promise<string | null> {
  const access = getAccessToken();
  if (access && !accessNeedsRefresh()) return access;
  const session = await refreshSession();
  return session?.accessToken ?? getAccessToken();
}

export async function refreshSession(): Promise<SessionTokens | null> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "Accept-Language": acceptLanguage(),
        },
        body: JSON.stringify({}),
      });

      const body = await response.json().catch(() => null);
      if (!response.ok) {
        clearSession();
        return null;
      }

      const data = body?.data as PersistedTokenResponse | undefined;
      if (!data?.accessToken || !data?.expiresIn) {
        clearSession();
        return null;
      }

      return setSession(data);
    } catch {
      clearSession();
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

/** Bootstrap session after reload using refresh cookie. */
export async function bootstrapSession(): Promise<SessionTokens | null> {
  if (getAccessToken() && !accessNeedsRefresh()) {
    return getSession();
  }
  return refreshSession();
}

export async function revokeSessionRemote(): Promise<void> {
  let access = getAccessToken();
  if (!access || accessNeedsRefresh()) {
    const refreshed = await refreshSession();
    access = refreshed?.accessToken ?? null;
  }

  try {
    await fetch(`${API_BASE}/api/v1/auth/logout`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": acceptLanguage(),
        ...(access ? { Authorization: `Bearer ${access}` } : {}),
      },
      body: JSON.stringify({}),
    });
  } catch {
    // Local logout always proceeds.
  }
}
