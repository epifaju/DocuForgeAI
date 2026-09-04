import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { fetchDashboard } from "@/api/dashboard";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

export function DashboardPage() {
  const { token } = useAuth();
  const { t, i18n } = useTranslation();
  const dash = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => fetchDashboard(token!),
    enabled: !!token,
    refetchInterval: 30_000,
  });

  const kpis = dash.data?.kpis;
  const loc = dateLocale(i18n.language);

  return (
    <AppShell title={t("dashboard.title")} description={t("dashboard.description")} width="wide">
      {dash.isLoading ? <p>{t("common.loading")}</p> : null}
      {dash.isError ? <p className="text-[var(--danger)]">{t("dashboard.unavailable")}</p> : null}

      {kpis ? (
        <div className="space-y-8">
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Kpi
              label={t("dashboard.docsToday")}
              value={kpis.documentsGeneratedToday}
              hint={t("dashboard.docsTodayHint")}
              to="/documents"
            />
            <Kpi
              label={t("dashboard.docsMonth")}
              value={kpis.documentsGeneratedThisMonth}
              hint={t("dashboard.docsMonthHint")}
              to="/documents"
            />
            <Kpi
              label={t("dashboard.activeTemplates")}
              value={kpis.activeTemplates}
              hint={t("dashboard.activeTemplatesHint")}
              to="/templates"
            />
            <Kpi
              label={t("dashboard.failures")}
              value={kpis.failedGenerations}
              hint={t("dashboard.failuresHint")}
              emphasis={kpis.failedGenerations > 0}
              to="/documents"
            />
          </section>

          <section className="flex flex-wrap gap-x-6 gap-y-2 rounded-2xl border border-[var(--line)] bg-[var(--surface)] px-5 py-3 text-sm text-[var(--muted)]">
            <SecondaryStat label={t("dashboard.batchesTotal")} value={kpis.batchJobsTotal} to="/batches" />
            <SecondaryStat label={t("dashboard.batchesActive")} value={kpis.batchJobsActive} to="/batches" />
            <SecondaryStat label={t("dashboard.aiToday")} value={kpis.aiRequestsToday} />
            <SecondaryStat label={t("dashboard.aiMonth")} value={kpis.aiRequestsThisMonth} />
            <span className="ml-auto self-center text-xs">
              {t("dashboard.timezone", { tz: dash.data?.timezone })}
            </span>
          </section>

          <section className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 className="text-lg text-[var(--brand-ink)]">{t("dashboard.recentDocs")}</h2>
                <Link to="/documents" className="text-sm text-[var(--brand)] underline">
                  {t("dashboard.seeAll")}
                </Link>
              </div>
              <ul className="space-y-2 text-sm">
                {(dash.data?.recentDocuments ?? []).length === 0 ? (
                  <li className="text-[var(--muted)]">{t("dashboard.noDocs")}</li>
                ) : null}
                {(dash.data?.recentDocuments ?? []).map((doc) => (
                  <li
                    key={doc.id}
                    className="flex flex-wrap items-baseline justify-between gap-2 border-b border-[var(--line)] py-2 last:border-0"
                  >
                    <div>
                      <Link to={`/documents/${doc.id}`} className="text-[var(--brand)] underline">
                        {doc.reference}
                      </Link>
                      <span className="ml-2 text-[var(--muted)]">{doc.title}</span>
                    </div>
                    <span className="flex items-center gap-2 text-xs text-[var(--muted)]">
                      <StatusBadge status={doc.status} />
                      {new Date(doc.createdAt).toLocaleString(loc)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 className="text-lg text-[var(--brand-ink)]">{t("dashboard.recentActivity")}</h2>
                <Link to="/audit" className="text-sm text-[var(--brand)] underline">
                  {t("dashboard.auditLink")}
                </Link>
              </div>
              <ul className="space-y-2 text-sm">
                {(dash.data?.recentActivity ?? []).length === 0 ? (
                  <li className="text-[var(--muted)]">{t("dashboard.noActivity")}</li>
                ) : null}
                {(dash.data?.recentActivity ?? []).map((row) => (
                  <li
                    key={row.id}
                    className="flex flex-wrap items-baseline justify-between gap-2 border-b border-[var(--line)] py-2 last:border-0"
                  >
                    <div>
                      <span className="font-mono text-xs">{row.action}</span>
                      <span className="ml-2 text-[var(--muted)]">{row.userEmail ?? "—"}</span>
                    </div>
                    <span className="flex items-center gap-2 text-xs text-[var(--muted)]">
                      <StatusBadge status={row.status} />
                      {new Date(row.createdAt).toLocaleString(loc)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </section>
        </div>
      ) : null}
    </AppShell>
  );
}

function Kpi({
  label,
  value,
  hint,
  to,
  emphasis,
}: {
  label: string;
  value: number;
  hint: string;
  to?: string;
  emphasis?: boolean;
}) {
  const inner = (
    <>
      <p className="text-xs uppercase tracking-wide text-[var(--muted)]">{label}</p>
      <p className={`mt-2 text-4xl ${emphasis ? "text-[var(--danger)]" : "text-[var(--brand-ink)]"}`}>
        {value}
      </p>
      <p className="mt-1 text-xs text-[var(--muted)]">{hint}</p>
    </>
  );
  const className =
    "block rounded-2xl border border-[var(--line)] bg-[var(--surface)] px-5 py-5 transition hover:border-[var(--brand)]";
  if (to) {
    return (
      <Link to={to} className={className}>
        {inner}
      </Link>
    );
  }
  return <div className={className}>{inner}</div>;
}

function SecondaryStat({ label, value, to }: { label: string; value: number; to?: string }) {
  const content = (
    <>
      <span className="text-[var(--muted)]">{label}</span>{" "}
      <strong className="text-[var(--brand-ink)]">{value}</strong>
    </>
  );
  if (to) {
    return (
      <Link to={to} className="hover:text-[var(--brand)]">
        {content}
      </Link>
    );
  }
  return <span>{content}</span>;
}
