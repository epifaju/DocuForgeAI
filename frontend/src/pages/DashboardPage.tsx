import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { fetchDashboard } from "@/api/dashboard";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { FailuresAlertBanner } from "@/components/FailuresAlertBanner";
import { QuickActionsCard } from "@/components/QuickActionsCard";
import { StatusBadge } from "@/components/StatusBadge";
import { buttonVariants } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { dateLocale } from "@/i18n";
import { cn } from "@/lib/cn";

export function DashboardPage() {
  const { token, hasRole, user } = useAuth();
  const { t, i18n } = useTranslation();
  const canViewAudit = hasRole("ADMIN", "EDITOR");
  const dash = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => fetchDashboard(token!),
    enabled: !!token,
    refetchInterval: 30_000,
  });

  const kpis = dash.data?.kpis;
  const loc = dateLocale(i18n.language);
  const docs = dash.data?.recentDocuments ?? [];
  const activity = dash.data?.recentActivity ?? [];
  const greetingName =
    user?.firstName?.trim() ||
    user?.lastName?.trim() ||
    user?.email?.split("@")[0] ||
    t("dashboard.greetingFallback");

  return (
    <AppShell
      title={t("dashboard.docsToday")}
      titleClassName="brand font-normal"
      eyebrow={t("dashboard.greeting", { name: greetingName })}
      width="wide"
      actions={
        <Link
          to="/templates"
          className={cn(buttonVariants({ variant: "primary", size: "md" }), "min-h-10")}
        >
          <Plus className="h-4 w-4" aria-hidden />
          {t("dashboard.newDocument")}
        </Link>
      }
    >
      {dash.isLoading ? <DashboardSkeleton /> : null}
      {dash.isError ? <p className="text-[var(--danger)]">{t("dashboard.unavailable")}</p> : null}

      {kpis ? (
        <div className="space-y-6">
          <section
            className="dash-rise grid gap-4 md:grid-cols-[1.35fr_1fr_1fr]"
            style={{ animationDelay: "40ms" }}
          >
            <Card padding="lg">
              <p className="text-sm text-[var(--muted)]">{t("dashboard.docsTodayHint")}</p>
              <div className="mt-3 flex items-end justify-between gap-4">
                <p
                  className={cn(
                    "dash-count brand text-5xl leading-none tracking-tight sm:text-6xl",
                    kpis.documentsGeneratedToday > 0
                      ? "text-[var(--brand-ink)]"
                      : "text-[var(--muted)]",
                  )}
                  style={{ animationDelay: "100ms" }}
                >
                  {kpis.documentsGeneratedToday}
                </p>
                {/*
                  Sparkline 7 jours non affichée : l'API /api/v1/dashboard ne fournit
                  que des totaux (today / month), pas une série quotidienne.
                */}
              </div>
              <Link
                to="/documents"
                className="mt-4 inline-flex text-sm font-medium text-[var(--brand)] hover:text-[var(--brand-ink)]"
              >
                {t("dashboard.openDocuments")}
              </Link>
            </Card>

            <KpiCard
              label={t("dashboard.docsMonth")}
              value={kpis.documentsGeneratedThisMonth}
              hint={t("dashboard.docsMonthHint")}
              to="/documents"
              delay="140ms"
            />
            <KpiCard
              label={t("dashboard.activeTemplates")}
              value={kpis.activeTemplates}
              hint={t("dashboard.activeTemplatesHint")}
              to="/templates?status=ACTIVE"
              delay="200ms"
            />
          </section>

          <FailuresAlertBanner count={kpis.failedGenerations} className="dash-rise" />

          <section
            className="dash-rise grid gap-5 md:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)]"
            style={{ animationDelay: "180ms" }}
          >
            <FeedPanel
              title={t("dashboard.recentDocs")}
              linkTo="/documents"
              linkLabel={t("dashboard.seeAll")}
              empty={
                <div className="flex flex-col items-start gap-3 py-2">
                  <p className="text-sm text-[var(--muted)]">{t("dashboard.noDocs")}</p>
                  <Link
                    to="/templates"
                    className={cn(buttonVariants({ variant: "secondary", size: "sm" }))}
                  >
                    {t("dashboard.emptyDocsCta")}
                  </Link>
                </div>
              }
              isEmpty={docs.length === 0}
            >
              {docs.map((doc, i) => (
                <li key={doc.id} className="dash-rise" style={{ animationDelay: `${220 + i * 40}ms` }}>
                  <Link
                    to={`/documents/${doc.id}`}
                    className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 px-4 py-3.5 transition-colors hover:bg-[var(--bg-accent)]/55 sm:px-5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-[var(--brand-ink)]">
                        {doc.title}
                      </p>
                      <p className="mt-0.5 truncate text-xs text-[var(--muted)]">
                        <span className="font-mono">{doc.reference}</span>
                        {" · "}
                        <time dateTime={doc.createdAt} title={new Date(doc.createdAt).toLocaleString(loc)}>
                          {formatRelative(doc.createdAt, loc)}
                        </time>
                      </p>
                    </div>
                    <StatusBadge
                      status={doc.status}
                      label={t(`documents.statusLabel.${doc.status}`, { defaultValue: doc.status })}
                    />
                  </Link>
                </li>
              ))}
            </FeedPanel>

            <div className="flex flex-col gap-5">
              <QuickActionsCard />
              <FeedPanel
                title={t("dashboard.recentActivity")}
                linkTo={canViewAudit ? "/audit" : undefined}
                linkLabel={canViewAudit ? t("dashboard.auditLink") : undefined}
                empty={<p className="py-2 text-sm text-[var(--muted)]">{t("dashboard.noActivity")}</p>}
                isEmpty={activity.length === 0}
              >
                {activity.map((row, i) => (
                  <li
                    key={row.id}
                    className="dash-rise px-4 py-3.5 sm:px-5"
                    style={{ animationDelay: `${260 + i * 40}ms` }}
                  >
                    <p className="text-sm text-[var(--muted)]">
                      <span className="font-medium text-[var(--brand-ink)]">
                        {t(`audit.actionLabel.${row.action}`, { defaultValue: row.action })}
                      </span>
                      {row.userEmail ? <> {row.userEmail}</> : null}
                      {" — "}
                      <time dateTime={row.createdAt} title={new Date(row.createdAt).toLocaleString(loc)}>
                        {formatRelative(row.createdAt, loc)}
                      </time>
                    </p>
                  </li>
                ))}
              </FeedPanel>
            </div>
          </section>
        </div>
      ) : null}
    </AppShell>
  );
}

function KpiCard({
  label,
  value,
  hint,
  to,
  delay,
  className,
}: {
  label: string;
  value: number;
  hint: string;
  to?: string;
  delay?: string;
  className?: string;
}) {
  const body = (
    <>
      <p className="text-sm text-[var(--muted)]">{label}</p>
      <p
        className="dash-count mt-3 text-4xl font-semibold tabular-nums tracking-tight text-[var(--brand-ink)] sm:text-5xl"
        style={{ animationDelay: delay }}
      >
        {value}
      </p>
      <p className="mt-1.5 text-xs text-[var(--muted)]">{hint}</p>
    </>
  );

  if (to) {
    return (
      <Card
        padding="lg"
        className={cn(
          "transition-colors hover:bg-[var(--bg-accent)]/40 focus-within:bg-[var(--bg-accent)]/40",
          className,
        )}
      >
        <Link to={to} className="block h-full outline-none">
          {body}
        </Link>
      </Card>
    );
  }
  return (
    <Card padding="lg" className={className}>
      {body}
    </Card>
  );
}

function FeedPanel({
  title,
  linkTo,
  linkLabel,
  children,
  empty,
  isEmpty,
}: {
  title: string;
  linkTo?: string;
  linkLabel?: string;
  children: ReactNode;
  empty: ReactNode;
  isEmpty: boolean;
}) {
  return (
    <Card padding="none" className="overflow-hidden">
      <div className="flex items-center justify-between gap-3 border-b border-[var(--line)] px-4 py-3.5 sm:px-5">
        <h2 className="text-[15px] font-semibold tracking-tight text-[var(--brand-ink)]">{title}</h2>
        {linkTo && linkLabel ? (
          <Link
            to={linkTo}
            className="text-xs font-medium text-[var(--brand)] transition-colors hover:text-[var(--brand-ink)]"
          >
            {linkLabel}
          </Link>
        ) : null}
      </div>
      {isEmpty ? (
        <div className="px-4 py-8 sm:px-5">{empty}</div>
      ) : (
        <ul className="divide-y divide-[var(--line)]/70">{children}</ul>
      )}
    </Card>
  );
}

function formatRelative(iso: string, locale: string) {
  const date = new Date(iso);
  const diffSec = Math.round((date.getTime() - Date.now()) / 1000);
  const abs = Math.abs(diffSec);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  if (abs < 60) return rtf.format(diffSec, "second");
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), "hour");
  if (abs < 86400 * 30) return rtf.format(Math.round(diffSec / 86400), "day");
  return date.toLocaleDateString(locale, { day: "numeric", month: "short" });
}

function DashboardSkeleton() {
  return (
    <div className="animate-pulse space-y-6" aria-hidden>
      <div className="grid gap-4 md:grid-cols-[1.35fr_1fr_1fr]">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className="h-36 rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)]"
          />
        ))}
      </div>
      <div className="grid gap-5 md:grid-cols-2">
        <div className="h-64 rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)]" />
        <div className="space-y-5">
          <div className="h-36 rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)]" />
          <div className="h-40 rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)]" />
        </div>
      </div>
    </div>
  );
}
