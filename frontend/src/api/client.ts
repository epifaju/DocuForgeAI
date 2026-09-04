import type { ApiResponse, ErrorResponse } from "./types";
import i18n from "@/i18n";

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

function authHeaders(token?: string | null): HeadersInit {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "Accept-Language": acceptLanguage(),
  };
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

export async function apiJson<T>(
  path: string,
  options: RequestInit & { token?: string | null } = {},
): Promise<T> {
  const { token, ...init } = options;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...authHeaders(token),
      ...(init.headers ?? {}),
    },
  });

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