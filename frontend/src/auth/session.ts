import i18n from "@/i18n";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

const ACCESS_KEY = "docuforge.accessToken";
const REFRESH_KEY = "docuforge.refreshToken";
const EXPIRES_KEY = "docuforge.accessExpiresAt";

/** Refresh access token this many ms before expiry. */
export const ACCESS_SKEW_MS = 60_000;

export type PersistedTokenResponse = {
  accessToken: string;
  refreshToken: string;
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

export function subscribeSession(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify() {
  listeners.forEach((listener) => listener());
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function getAccessExpiresAt(): number | null {
  const raw = localStorage.getItem(EXPIRES_KEY);
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

export function getSession(): SessionTokens | null {
  const accessToken = getAccessToken();
  const refreshToken = getRefreshToken();
  const expiresAt = getAccessExpiresAt();
  if (!accessToken || !refreshToken || expiresAt == null) return null;
  return { accessToken, refreshToken, expiresAt };
}

export function setSession(tokens: PersistedTokenResponse): SessionTokens {
  const expiresAt = Date.now() + Math.max(1, tokens.expiresIn) * 1000;
  localStorage.setItem(ACCESS_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  localStorage.setItem(EXPIRES_KEY, String(expiresAt));
  notify();
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt,
  };
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(EXPIRES_KEY);
  notify();
}

export function accessNeedsRefresh(now = Date.now()): boolean {
  const expiresAt = getAccessExpiresAt();
  if (expiresAt == null) return !!getRefreshToken();
  return expiresAt - now <= ACCESS_SKEW_MS;
}

/**
 * Ensure a usable access token, refreshing when close to expiry.
 * Concurrent callers share one refresh request (required: refresh tokens rotate).
 */
export async function ensureFreshAccessToken(): Promise<string | null> {
  const access = getAccessToken();
  if (!access) return null;
  if (!accessNeedsRefresh()) return access;
  const session = await refreshSession();
  return session?.accessToken ?? getAccessToken();
}

export async function refreshSession(): Promise<SessionTokens | null> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      clearSession();
      return null;
    }

    try {
      const response = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept-Language": acceptLanguage(),
        },
        body: JSON.stringify({ refreshToken }),
      });

      const body = await response.json().catch(() => null);
      if (!response.ok) {
        clearSession();
        return null;
      }

      const data = body?.data as PersistedTokenResponse | undefined;
      if (!data?.accessToken || !data?.refreshToken || !data?.expiresIn) {
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

export async function revokeSessionRemote(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return;

  let access = getAccessToken();
  if (!access || accessNeedsRefresh()) {
    const refreshed = await refreshSession();
    access = refreshed?.accessToken ?? null;
  }
  if (!access) return;

  const currentRefresh = getRefreshToken();
  if (!currentRefresh) return;

  try {
    await fetch(`${API_BASE}/api/v1/auth/logout`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": acceptLanguage(),
        Authorization: `Bearer ${access}`,
      },
      body: JSON.stringify({ refreshToken: currentRefresh }),
    });
  } catch {
    // Local logout always proceeds.
  }
}
