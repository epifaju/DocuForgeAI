import { apiJson } from "./client";

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface MeResponse {
  id?: string;
  email: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
  companyIdentifier: string;
  companyName?: string;
}

export function login(companyIdentifier: string, email: string, password: string) {
  return apiJson<TokenResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ companyIdentifier, email, password }),
    skipAuthRefresh: true,
  });
}

export function me(token: string) {
  return apiJson<MeResponse>("/api/v1/auth/me", { token });
}

export function forgotPassword(companyIdentifier: string, email: string) {
  return apiJson<null>("/api/v1/auth/forgot-password", {
    method: "POST",
    body: JSON.stringify({ companyIdentifier, email }),
    skipAuthRefresh: true,
  });
}

export function resetPassword(token: string, newPassword: string) {
  return apiJson<null>("/api/v1/auth/reset-password", {
    method: "POST",
    body: JSON.stringify({ token, newPassword }),
    skipAuthRefresh: true,
  });
}
