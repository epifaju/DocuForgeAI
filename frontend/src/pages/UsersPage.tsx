import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Navigate } from "react-router-dom";
import { createUser, listUsers, updateUser } from "@/api/users";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { CreatePanel } from "@/components/CreatePanel";
import { StatusBadge } from "@/components/StatusBadge";
import { isBlank, isValidEmail } from "@/lib/formValidation";

const ROLE_OPTIONS = ["ADMIN", "EDITOR", "USER", "VIEWER"] as const;

export function UsersPage() {
  const { t } = useTranslation();
  const { token, isAdmin, user: me } = useAuth();
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [roles, setRoles] = useState<string[]>(["USER"]);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const query = useQuery({
    queryKey: ["admin-users"],
    queryFn: () => listUsers(token!),
    enabled: !!token && isAdmin,
  });

  const create = useMutation({
    mutationFn: () =>
      createUser(token!, {
        email: email.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        roles,
        enabled: true,
      }),
    onSuccess: (u) => {
      setMessage(t("users.created", { email: u.email }));
      setEmail("");
      setPassword("");
      setFirstName("");
      setLastName("");
      setRoles(["USER"]);
      setShowCreate(false);
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const toggle = useMutation({
    mutationFn: (row: {
      id: string;
      firstName: string;
      lastName: string;
      roles: string[];
      enabled: boolean;
    }) =>
      updateUser(token!, row.id, {
        firstName: row.firstName,
        lastName: row.lastName,
        roles: row.roles,
        enabled: !row.enabled,
      }),
    onSuccess: () => {
      setMessage(t("users.updated"));
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  function onCreate(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (isBlank(firstName)) next.firstName = t("validation.required");
    if (isBlank(lastName)) next.lastName = t("validation.required");
    if (isBlank(email)) next.email = t("validation.required");
    else if (!isValidEmail(email)) next.email = t("validation.email");
    if (isBlank(password)) next.password = t("validation.required");
    else if (password.length < 8) next.password = t("validation.passwordMin", { min: 8 });
    if (roles.length === 0) next.roles = t("validation.rolesRequired");
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;
    setMessage(null);
    create.mutate();
  }

  function toggleRole(role: string) {
    setRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  }

  const items = query.data?.items ?? [];

  return (
    <AppShell
      title={t("users.title")}
      description={t("users.description")}
      width="wide"
      actions={
        <button
          type="button"
          onClick={() => setShowCreate((v) => !v)}
          className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
        >
          {showCreate ? t("users.hide") : t("users.new")}
        </button>
      }
    >
      <CreatePanel
        open={showCreate}
        title={t("users.panelTitle")}
        onClose={() => setShowCreate(false)}
      >
        <form onSubmit={onCreate} noValidate className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              {t("users.firstName")}
              <input
                className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                aria-invalid={!!fieldErrors.firstName}
              />
              {fieldErrors.firstName ? (
                <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.firstName}</p>
              ) : null}
            </label>
            <label className="block text-sm">
              {t("users.lastName")}
              <input
                className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                aria-invalid={!!fieldErrors.lastName}
              />
              {fieldErrors.lastName ? (
                <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.lastName}</p>
              ) : null}
            </label>
          </div>
          <label className="block text-sm">
            {t("users.email")}
            <input
              type="email"
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={!!fieldErrors.email}
            />
            {fieldErrors.email ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.email}</p>
            ) : null}
          </label>
          <label className="block text-sm">
            {t("users.password")}
            <input
              type="password"
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!fieldErrors.password}
            />
            {fieldErrors.password ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.password}</p>
            ) : null}
          </label>
          <fieldset className="text-sm">
            <legend className="mb-2">{t("users.roles")}</legend>
            <div className="flex flex-wrap gap-3">
              {ROLE_OPTIONS.map((role) => (
                <label key={role} className="inline-flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={roles.includes(role)}
                    onChange={() => toggleRole(role)}
                  />
                  {role}
                </label>
              ))}
            </div>
            {fieldErrors.roles ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.roles}</p>
            ) : null}
          </fieldset>
          <button
            type="submit"
            disabled={create.isPending}
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {create.isPending ? t("common.creating") : t("common.create")}
          </button>
        </form>
      </CreatePanel>

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="text-[var(--danger)]">{t("users.loadError")}</p>
      ) : null}

      <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-[var(--line)] text-[var(--muted)]">
            <tr>
              <th className="px-4 py-3 font-medium">{t("users.colName")}</th>
              <th className="px-4 py-3 font-medium">{t("users.colEmail")}</th>
              <th className="px-4 py-3 font-medium">{t("users.colRoles")}</th>
              <th className="px-4 py-3 font-medium">{t("users.colStatus")}</th>
              <th className="px-4 py-3 font-medium">{t("users.colActions")}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((u) => (
              <tr key={u.id} className="border-b border-[var(--line)] last:border-0">
                <td className="px-4 py-3 font-medium">
                  {u.firstName} {u.lastName}
                </td>
                <td className="px-4 py-3">{u.email}</td>
                <td className="px-4 py-3 text-[var(--muted)]">{(u.roles ?? []).join(", ")}</td>
                <td className="px-4 py-3">
                  <StatusBadge
                    status={u.enabled ? "ACTIVE" : "DISABLED"}
                    label={u.enabled ? t("users.active") : t("users.disabled")}
                  />
                </td>
                <td className="px-4 py-3">
                  <button
                    type="button"
                    disabled={toggle.isPending || u.id === me?.id}
                    className="text-sm text-[var(--brand)] underline disabled:opacity-40"
                    onClick={() =>
                      toggle.mutate({
                        id: u.id,
                        firstName: u.firstName,
                        lastName: u.lastName,
                        roles: u.roles,
                        enabled: u.enabled,
                      })
                    }
                  >
                    {u.enabled ? t("users.disable") : t("users.enable")}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!query.isLoading && items.length === 0 ? (
        <p className="mt-6 text-[var(--muted)]">{t("users.empty")}</p>
      ) : null}
    </AppShell>
  );
}
