import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";

export type ShellWidth = "wide" | "default" | "form" | "narrow";

const WIDTH_CLASS: Record<ShellWidth, string> = {
  wide: "max-w-6xl",
  default: "max-w-5xl",
  form: "max-w-4xl",
  narrow: "max-w-3xl",
};

const NAV = [
  { to: "/dashboard", labelKey: "nav.dashboard", end: true },
  { to: "/templates", labelKey: "nav.templates" },
  { to: "/documents", labelKey: "nav.documents" },
  { to: "/batches", labelKey: "nav.batches" },
  { to: "/audit", labelKey: "nav.audit" },
] as const;

const ADMIN_NAV = [
  { to: "/settings", labelKey: "nav.settings" },
  { to: "/users", labelKey: "nav.users" },
] as const;

function linkClass({ isActive }: { isActive: boolean }) {
  return [
    "whitespace-nowrap border-b-2 px-0.5 pb-2 text-sm transition-colors",
    isActive
      ? "border-[var(--brand)] font-medium text-[var(--brand-ink)]"
      : "border-transparent text-[var(--muted)] hover:text-[var(--brand)]",
  ].join(" ");
}

export function AppShell({
  title,
  description,
  width = "wide",
  children,
  actions,
}: {
  title: string;
  description?: string;
  width?: ShellWidth;
  children: ReactNode;
  actions?: ReactNode;
}) {
  const { logout, isAdmin, user } = useAuth();
  const { t } = useTranslation();

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-[var(--line)] bg-[var(--surface)]/90 backdrop-blur-md">
        <div
          className={`mx-auto flex ${WIDTH_CLASS.wide} flex-wrap items-center justify-between gap-x-6 gap-y-2 px-6 py-3`}
        >
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-6 gap-y-2">
            <NavLink to="/dashboard" className="brand shrink-0 text-xl text-[var(--brand-ink)]">
              DocuForge AI
            </NavLink>
            <nav
              className="flex min-w-0 flex-1 gap-4 overflow-x-auto"
              aria-label={t("common.navAria")}
            >
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={"end" in item ? item.end : false}
                  className={linkClass}
                >
                  {t(item.labelKey)}
                </NavLink>
              ))}
              {isAdmin
                ? ADMIN_NAV.map((item) => (
                    <NavLink key={item.to} to={item.to} className={linkClass}>
                      {t(item.labelKey)}
                    </NavLink>
                  ))
                : null}
            </nav>
          </div>
          <div className="flex shrink-0 items-center gap-3 text-sm">
            <LanguageSwitcher />
            {user?.email ? (
              <span className="hidden text-[var(--muted)] sm:inline" title={user.companyIdentifier}>
                {user.email}
              </span>
            ) : null}
            <button
              type="button"
              onClick={logout}
              className="text-[var(--muted)] underline-offset-2 hover:text-[var(--brand-ink)] hover:underline"
            >
              {t("common.logout")}
            </button>
          </div>
        </div>
      </header>

      <main className={`mx-auto ${WIDTH_CLASS[width]} px-6 py-8`}>
        <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="brand text-3xl text-[var(--brand-ink)]">{title}</h1>
            {description ? <p className="mt-1 max-w-2xl text-[var(--muted)]">{description}</p> : null}
          </div>
          {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
        </div>
        {children}
      </main>
    </div>
  );
}
