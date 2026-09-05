import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useLocation } from "react-router-dom";
import { getSettings, updateSettings } from "@/api/settings";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { isBlank } from "@/lib/formValidation";

type Section = "company" | "ai" | "email" | "privacy" | "users";

function sectionFromPath(pathname: string): Section {
  if (pathname.endsWith("/ai")) return "ai";
  if (pathname.endsWith("/email")) return "email";
  if (pathname.endsWith("/privacy")) return "privacy";
  if (pathname.endsWith("/users")) return "users";
  return "company";
}

export function SettingsPage() {
  const { t } = useTranslation();
  const { token, isAdmin } = useAuth();
  const location = useLocation();
  const section = sectionFromPath(location.pathname);
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [companyAi, setCompanyAi] = useState(true);
  const [fromAddress, setFromAddress] = useState("");
  const [retentionDays, setRetentionDays] = useState(365);
  const [message, setMessage] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["admin-settings"],
    queryFn: () => getSettings(token!),
    enabled: !!token && isAdmin,
  });

  useEffect(() => {
    if (!query.data) return;
    setName(query.data.company.name);
    setCompanyAi(query.data.ai.companyEnabled);
    setFromAddress(query.data.email.fromAddress ?? "");
    setRetentionDays(query.data.privacy?.retentionDays ?? 365);
  }, [query.data]);

  const save = useMutation({
    mutationFn: () =>
      updateSettings(token!, {
        company: { name: name.trim() },
        ai: { companyEnabled: companyAi },
        email: { fromAddress: fromAddress.trim() },
        privacy: { retentionDays },
      }),
    onSuccess: () => {
      setMessage(t("settings.saved"));
      void queryClient.invalidateQueries({ queryKey: ["admin-settings"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  if (section === "users") {
    return <Navigate to="/users" replace />;
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (section === "company" && isBlank(name)) {
      setNameError(t("validation.required"));
      return;
    }
    setNameError(null);
    setMessage(null);
    save.mutate();
  }

  const data = query.data;

  return (
    <AppShell
      title={t("settings.title")}
      description={t("settings.description")}
      width="narrow"
    >
      <nav className="mb-6 flex flex-wrap gap-3 text-sm">
        <SectionLink to="/settings/company" active={section === "company"}>
          {t("settings.company")}
        </SectionLink>
        <SectionLink to="/settings/ai" active={section === "ai"}>
          {t("settings.ai")}
        </SectionLink>
        <SectionLink to="/settings/email" active={section === "email"}>
          {t("settings.email")}
        </SectionLink>
        <SectionLink to="/settings/privacy" active={section === "privacy"}>
          {t("settings.privacy")}
        </SectionLink>
        <SectionLink to="/privacy" active={false}>
          {t("settings.myData")}
        </SectionLink>
        <SectionLink to="/users" active={false}>
          {t("settings.usersLink")}
        </SectionLink>
      </nav>

      {query.isLoading ? <p className="text-[var(--muted)]">{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="text-[var(--danger)]">{(query.error as Error).message}</p>
      ) : null}

      {data ? (
        <form
          onSubmit={onSubmit}
          noValidate
          className="space-y-6 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5"
        >
          {section === "company" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">{t("settings.company")}</p>
              <label className="block text-sm">
                {t("settings.displayName")}
                <input
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={200}
                  aria-invalid={!!nameError}
                />
                {nameError ? <p className="mt-1 text-sm text-[var(--danger)]">{nameError}</p> : null}
              </label>
              <label className="block text-sm">
                {t("settings.identifier")}
                <input
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-[var(--bg-accent)] px-3 py-2 text-[var(--muted)]"
                  value={data.company.identifier}
                  readOnly
                />
              </label>
              <p className="text-xs text-[var(--muted)]">{t("settings.identifierHint")}</p>
            </div>
          ) : null}

          {section === "ai" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">{t("settings.aiTitle")}</p>
              <p className="text-sm text-[var(--muted)]">
                {t("settings.platform", {
                  state: data.ai.platformEnabled
                    ? t("settings.platformOn")
                    : t("settings.platformOff"),
                  provider: data.ai.provider,
                  model: data.ai.model,
                })}
              </p>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={companyAi}
                  onChange={(e) => setCompanyAi(e.target.checked)}
                />
                {t("settings.companyAi")}
              </label>
              <p className="text-xs text-[var(--muted)]">
                {t("settings.effective", {
                  state:
                    data.ai.platformEnabled && companyAi
                      ? t("settings.available")
                      : t("settings.unavailable"),
                })}
              </p>
            </div>
          ) : null}

          {section === "email" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">{t("settings.emailTitle")}</p>
              <p className="text-sm text-[var(--muted)]">
                {t("settings.smtp", {
                  state: data.email.platformEnabled
                    ? t("settings.smtpOn")
                    : t("settings.smtpOff"),
                  from: data.email.platformFrom || "—",
                })}
              </p>
              <label className="block text-sm">
                {t("settings.fromOverride")}
                <input
                  type="email"
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  value={fromAddress}
                  onChange={(e) => setFromAddress(e.target.value)}
                  placeholder={data.email.platformFrom || "noreply@example.com"}
                />
              </label>
              <p className="text-xs text-[var(--muted)]">{t("settings.fromHint")}</p>
            </div>
          ) : null}

          {section === "privacy" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">{t("settings.privacyTitle")}</p>
              <p className="text-sm text-[var(--muted)]">{t("settings.privacyHint")}</p>
              <label className="block text-sm">
                {t("settings.retentionDays")}
                <input
                  type="number"
                  min={30}
                  max={3650}
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  value={retentionDays}
                  onChange={(e) => setRetentionDays(Number(e.target.value) || 365)}
                />
              </label>
            </div>
          ) : null}

          {message ? <p className="text-sm text-[var(--brand)]">{message}</p> : null}

          <button
            type="submit"
            disabled={save.isPending}
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm text-white disabled:opacity-60"
          >
            {save.isPending ? t("common.saving") : t("common.save")}
          </button>
        </form>
      ) : null}
    </AppShell>
  );
}

function SectionLink({
  to,
  active,
  children,
}: {
  to: string;
  active: boolean;
  children: string;
}) {
  return (
    <Link
      to={to}
      className={
        active
          ? "font-medium text-[var(--brand-ink)] underline"
          : "text-[var(--brand)] underline-offset-2 hover:underline"
      }
    >
      {children}
    </Link>
  );
}
