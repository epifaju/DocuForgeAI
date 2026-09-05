import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useParams } from "react-router-dom";
import { getUser, updateUser } from "@/api/users";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";
import { isBlank } from "@/lib/formValidation";

const ROLE_OPTIONS = ["ADMIN", "EDITOR", "USER", "VIEWER"] as const;

export function UserDetailPage() {
  const { t, i18n } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const { token, isAdmin, user: me } = useAuth();
  const queryClient = useQueryClient();
  const loc = dateLocale(i18n.language);

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [roles, setRoles] = useState<string[]>([]);
  const [enabled, setEnabled] = useState(true);
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["admin-user", id],
    queryFn: () => getUser(token!, id!),
    enabled: !!token && isAdmin && !!id,
  });

  const user = query.data;
  const isSelf = !!user && !!me?.id && user.id === me.id;

  useEffect(() => {
    if (!user) return;
    setFirstName(user.firstName);
    setLastName(user.lastName);
    setRoles([...(user.roles ?? [])]);
    setEnabled(user.enabled);
    setPassword("");
    setFieldErrors({});
  }, [user]);

  const save = useMutation({
    mutationFn: () => {
      const body: {
        firstName: string;
        lastName: string;
        roles: string[];
        enabled: boolean;
        password?: string;
      } = {
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        roles,
        enabled,
      };
      if (password.trim()) body.password = password;
      return updateUser(token!, id!, body);
    },
    onSuccess: (updated) => {
      setMessage(t("users.updated"));
      setPassword("");
      void queryClient.setQueryData(["admin-user", id], updated);
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  function toggleRole(role: string) {
    setRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (isBlank(firstName)) next.firstName = t("validation.required");
    if (isBlank(lastName)) next.lastName = t("validation.required");
    if (roles.length === 0) next.roles = t("validation.rolesRequired");
    if (password && password.length < 8) {
      next.password = t("validation.passwordMin", { min: 8 });
    }
    if (isSelf && !enabled) next.enabled = t("users.cannotDisableSelf");
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;
    setMessage(null);
    save.mutate();
  }

  return (
    <AppShell
      title={
        user
          ? `${user.firstName} ${user.lastName}`
          : t("users.detailTitle")
      }
      description={user?.email ?? t("users.detailDescription")}
      width="form"
      actions={
        <Link
          to="/users"
          className="rounded-xl border border-[var(--line)] bg-[var(--surface)] px-3.5 py-2 text-sm text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]"
        >
          {t("users.back")}
        </Link>
      }
    >
      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="text-[var(--danger)]">{t("users.notFound")}</p>
      ) : null}

      {user ? (
        <div className="space-y-6">
          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                  {t("users.email")}
                </p>
                <p className="mt-1 text-[var(--brand-ink)]">{user.email}</p>
                <p className="mt-1 text-xs text-[var(--muted)]">{t("users.emailImmutable")}</p>
              </div>
              <StatusBadge
                status={user.enabled ? "ACTIVE" : "DISABLED"}
                label={user.enabled ? t("users.active") : t("users.disabled")}
              />
            </div>
            <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-[var(--muted)]">{t("users.createdAt")}</dt>
                <dd className="mt-0.5 tabular-nums text-[var(--brand-ink)]">
                  {new Date(user.createdAt).toLocaleString(loc)}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("users.updatedAt")}</dt>
                <dd className="mt-0.5 tabular-nums text-[var(--brand-ink)]">
                  {new Date(user.updatedAt).toLocaleString(loc)}
                </dd>
              </div>
            </dl>
          </section>

          <form
            onSubmit={onSubmit}
            noValidate
            className="space-y-4 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6"
          >
            <h2 className="text-lg text-[var(--brand-ink)]">{t("users.editTitle")}</h2>

            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm">
                {t("users.firstName")}
                <input
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 outline-none focus:border-[var(--brand)]"
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
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 outline-none focus:border-[var(--brand)]"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  aria-invalid={!!fieldErrors.lastName}
                />
                {fieldErrors.lastName ? (
                  <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.lastName}</p>
                ) : null}
              </label>
            </div>

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

            <label className="flex items-start gap-3 text-sm">
              <input
                type="checkbox"
                className="mt-1"
                checked={enabled}
                disabled={isSelf}
                onChange={(e) => setEnabled(e.target.checked)}
              />
              <span>
                <span className="font-medium text-[var(--brand-ink)]">{t("users.enabledLabel")}</span>
                {isSelf ? (
                  <span className="mt-0.5 block text-xs text-[var(--muted)]">
                    {t("users.cannotDisableSelf")}
                  </span>
                ) : null}
                {fieldErrors.enabled ? (
                  <span className="mt-0.5 block text-sm text-[var(--danger)]">
                    {fieldErrors.enabled}
                  </span>
                ) : null}
              </span>
            </label>

            <label className="block text-sm">
              {t("users.passwordOptional")}
              <input
                type="password"
                autoComplete="new-password"
                className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 outline-none focus:border-[var(--brand)]"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t("users.passwordOptionalHint")}
                aria-invalid={!!fieldErrors.password}
              />
              {fieldErrors.password ? (
                <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.password}</p>
              ) : (
                <p className="mt-1 text-xs text-[var(--muted)]">{t("users.passwordOptionalHint")}</p>
              )}
            </label>

            {message ? (
              <p className={`text-sm ${save.isError ? "text-[var(--danger)]" : "text-[var(--brand)]"}`}>
                {message}
              </p>
            ) : null}

            <div className="flex flex-wrap gap-2 pt-1">
              <button
                type="submit"
                disabled={save.isPending}
                className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--brand-ink)] disabled:opacity-60"
              >
                {save.isPending ? t("common.saving") : t("common.save")}
              </button>
              <Link
                to="/users"
                className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm text-[var(--muted)] transition-colors hover:border-[var(--brand)] hover:text-[var(--brand-ink)]"
              >
                {t("common.cancel")}
              </Link>
            </div>
          </form>
        </div>
      ) : null}
    </AppShell>
  );
}
