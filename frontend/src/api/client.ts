import type { ApiResponse, ErrorResponse } from "./types";

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

function authHeaders(token?: string | null): HeadersInit {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
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