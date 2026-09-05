import type { ApiResponse, ErrorResponse } from "./types";
import i18n from "@/i18n";
import {
  clearSession,
  ensureFreshAccessToken,
  getAccessToken,
  refreshSession,
} from "@/auth/session";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: ErrorResponse["details"];

  constructor(body: ErrorResponse) {
    super(body.message);
    this.status = body.status;
    this.code = body.code;
    this.details = body.details ?? [];
  }
}

export function acceptLanguage(): string {
  return i18n.language?.toLowerCase().startsWith("pt") ? "pt" : "fr";
}

function isAuthPublicPath(path: string): boolean {
  return (
    path.startsWith("/api/v1/auth/login") ||
    path.startsWith("/api/v1/auth/refresh")
  );
}

function authHeaders(token?: string | null, jsonBody = true): HeadersInit {
  const headers: Record<string, string> = {
    "Accept-Language": acceptLanguage(),
  };
  if (jsonBody) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

/** Auth + Accept-Language for multipart / binary fetches (no Content-Type). */
export function bearerHeaders(token: string): HeadersInit {
  return {
    Authorization: `Bearer ${token}`,
    "Accept-Language": acceptLanguage(),
  };
}

export type ApiFetchOptions = RequestInit & {
  token?: string | null;
  /** Skip proactive refresh + 401 retry (login/refresh). */
  skipAuthRefresh?: boolean;
  /** When false, do not set Content-Type: application/json (FormData / binary). */
  json?: boolean;
};

/**
 * Authenticated fetch with proactive token refresh and one 401 retry.
 */
export async function apiFetch(path: string, options: ApiFetchOptions = {}): Promise<Response> {
  const { token, skipAuthRefresh, json = true, ...init } = options;
  const publicAuth = isAuthPublicPath(path);

  const send = async (access: string | null | undefined) =>
    fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        ...authHeaders(access, json),
        ...(init.headers ?? {}),
      },
    });

  let access = token ?? getAccessToken();
  if (!skipAuthRefresh && !publicAuth && access) {
    access = (await ensureFreshAccessToken()) ?? access;
  }

  let response = await send(access);

  if (
    response.status === 401 &&
    !skipAuthRefresh &&
    !publicAuth
  ) {
    const session = await refreshSession();
    if (session) {
      response = await send(session.accessToken);
    } else {
      clearSession();
    }
  }

  return response;
}

export async function apiJson<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  const response = await apiFetch(path, { json: true, ...options });

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      (body as ErrorResponse) ?? {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: "REQUEST_ERROR",
        message: "Requete impossible.",
        details: [],
      },
    );
  }
  return (body as ApiResponse<T>).data;
}
