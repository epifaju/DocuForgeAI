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
