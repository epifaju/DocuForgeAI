import { apiJson } from "./client";
import type { PageResponse } from "./types";

export interface AdminUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export function listUsers(token: string) {
  return apiJson<PageResponse<AdminUser>>("/api/v1/admin/users?size=100", { token });
}

export function getUser(token: string, id: string) {
  return apiJson<AdminUser>(`/api/v1/admin/users/${id}`, { token });
}

export function createUser(
  token: string,
  body: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    roles: string[];
    enabled?: boolean;
  },
) {
  return apiJson<AdminUser>("/api/v1/admin/users", {
    method: "POST",
    token,
    body: JSON.stringify(body),
  });
}

export function updateUser(
  token: string,
  id: string,
  body: {
    firstName: string;
    lastName: string;
    roles: string[];
    enabled: boolean;
    password?: string;
  },
) {
  return apiJson<AdminUser>(`/api/v1/admin/users/${id}`, {
    method: "PUT",
    token,
    body: JSON.stringify(body),
  });
}
