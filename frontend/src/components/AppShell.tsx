import { ChevronDown } from "lucide-react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, useLocation } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

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
] as const;

type AdminItem = { to: string; labelKey: string; match?: (path: string) => boolean };

function linkClass({ isActive }: { isActive: boolean }) {
  return cn(
    "inline-flex min-h-10 items-center whitespace-nowrap border-b-2 px-0.5 text-sm transition-colors",
    isActive
      ? "border-[var(--brand)] font-medium text-[var(--brand-ink)]"
      : "border-transparent text-[var(--muted)] hover:text-[var(--brand)]",
  );
}

export function AppShell({
  title,
  description,
  eyebrow,
  titleClassName,
  width = "wide",
  children,
  actions,
}: {
  title: string;
  description?: string;
  /** Optional line above the page title (e.g. greeting). */
  eyebrow?: ReactNode;
  titleClassName?: string;
  width?: ShellWidth;
  children: ReactNode;
  actions?: ReactNode;
}) {
  const { logout, isAdmin, hasRole, user } = useAuth();
  const { t } = useTranslation();
  const location = useLocation();
  const canViewAudit = hasRole("ADMIN", "EDITOR");
  const [adminOpen, setAdminOpen] = useState(false);
  const adminWrapRef = useRef<HTMLDivElement>(null);
  const adminMenuId = useId();

  const adminItems: AdminItem[] = [];
  if (isAdmin) {
    adminItems.push(
      { to: "/business-packs", labelKey: "nav.packs", match: (p) => p.startsWith("/business-packs") },
      { to: "/users", labelKey: "nav.users", match: (p) => p.startsWith("/users") },
      {
        to: "/settings/company",
        labelKey: "nav.settings",
        match: (p) => p.startsWith("/settings"),
      },
    );
  }
  if (canViewAudit) {
    adminItems.push({ to: "/audit", labelKey: "nav.audit", match: (p) => p.startsWith("/audit") });
  }
  // Privacy (RGPD) is available to every authenticated user.
  adminItems.push({ to: "/privacy", labelKey: "nav.privacy", match: (p) => p.startsWith("/privacy") });

  const showAdmin = adminItems.length > 0;
  const adminActive = adminItems.some((item) =>
    item.match ? item.match(location.pathname) : location.pathname === item.to,
  );

  useEffect(() => {
    setAdminOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!adminOpen) return;
    const onPointerDown = (event: MouseEvent) => {
      if (!adminWrapRef.current?.contains(event.target as Node)) {
        setAdminOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setAdminOpen(false);
    };
    window.addEventListener("mousedown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("mousedown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [adminOpen]);

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 overflow-visible border-b border-[var(--line)] bg-[var(--surface)]/90 backdrop-blur-md">
        <div
          className={`mx-auto flex ${WIDTH_CLASS.wide} flex-wrap items-center justify-between gap-x-6 gap-y-2 px-6 py-3`}
        >
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-6 gap-y-2">
            <NavLink to="/dashboard" className="brand shrink-0 text-xl text-[var(--brand-ink)]">
              DocuForge AI
            </NavLink>
            <nav
              className="flex min-w-0 flex-1 items-center gap-4 overflow-visible"
              aria-label={t("common.navAria")}
            >
              {/* Scroll primary links only — overflow here must not wrap Admin or it clips the dropdown. */}
              <div className="flex min-w-0 flex-1 items-center gap-4 overflow-x-auto">
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
              </div>
              {showAdmin ? (
                <div className="relative shrink-0" ref={adminWrapRef}>
                  <button
                    type="button"
                    className={cn(
                      linkClass({ isActive: adminActive }),
                      "gap-1",
                      adminOpen && "text-[var(--brand-ink)]",
                    )}
                    aria-expanded={adminOpen}
                    aria-haspopup="menu"
                    aria-controls={adminMenuId}
                    onClick={() => setAdminOpen((v) => !v)}
                  >
                    {t("nav.admin")}
                    <ChevronDown
                      className={cn("h-4 w-4 transition-transform", adminOpen && "rotate-180")}
                      aria-hidden
                    />
                  </button>
                  {adminOpen ? (
                    <div
                      id={adminMenuId}
                      role="menu"
                      aria-label={t("nav.admin")}
                      className="absolute right-0 top-full z-50 mt-1 min-w-[12rem] rounded-[var(--radius-lg)] border border-[var(--line)] bg-[var(--surface)] py-1 shadow-lg"
                    >
                      {adminItems.map((item) => (
                        <NavLink
                          key={item.to}
                          to={item.to}
                          role="menuitem"
                          className={({ isActive }) =>
                            cn(
                              "flex min-h-10 items-center px-3 text-sm transition-colors",
                              isActive
                                ? "bg-[var(--brand-soft)] font-medium text-[var(--brand-ink)]"
                                : "text-[var(--muted)] hover:bg-[var(--bg-accent)] hover:text-[var(--brand-ink)]",
                            )
                          }
                          onClick={() => setAdminOpen(false)}
                        >
                          {t(item.labelKey)}
                        </NavLink>
                      ))}
                    </div>
                  ) : null}
                </div>
              ) : null}
            </nav>
          </div>
          <div className="flex shrink-0 items-center gap-3 text-sm">
            <LanguageSwitcher />
            {user?.email ? (
              <span className="hidden text-[var(--muted)] sm:inline" title={user.companyIdentifier}>
                {user.email}
              </span>
            ) : null}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={logout}
              className="min-h-10 px-3"
            >
              {t("common.logout")}
            </Button>
          </div>
        </div>
      </header>

      <main className={`mx-auto ${WIDTH_CLASS[width]} px-6 py-8`}>
        <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div>
            {eyebrow ? (
              <p className="mb-1 text-sm text-[var(--muted)]">{eyebrow}</p>
            ) : null}
            <h1
              className={cn(
                "text-3xl font-semibold tracking-tight text-[var(--brand-ink)]",
                titleClassName,
              )}
            >
              {title}
            </h1>
            {description ? <p className="mt-1 max-w-2xl text-[var(--muted)]">{description}</p> : null}
          </div>
          {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
        </div>
        {children}
      </main>
    </div>
  );
}
