import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { fetchDashboard } from "@/api/dashboard";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

export function DashboardPage() {
  const { token } = useAuth();
  const dash = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => fetchDashboard(token!),
    enabled: !!token,
    refetchInterval: 30_000,
  });

  const kpis = dash.data?.kpis;

  return (
    <div className="mx-auto max-w-5xl px-6 py-10">
      <AppHeader subtitle="Tableau de bord — KPIs et activite recente." />

      {dash.isLoading ? <p>Chargement…</p> : null}
      {dash.isError ? <p className="text-[var(--danger)]">Dashboard indisponible.</p> : null}

      {kpis ? (
        <div className="space-y-8">
          <p className="text-sm text-[var(--muted)]">Fuseau : {dash.data?.timezone}</p>

          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi label="Documents aujourd'hui" value={kpis.documentsGeneratedToday} />
            <Kpi label="Documents ce mois" value={kpis.documentsGeneratedThisMonth} />
            <Kpi label="Templates actifs" value={kpis.activeTemplates} />
            <Kpi label="Generations echouees" value={kpis.failedGenerations} />
            <Kpi label="Batches (total)" value={kpis.batchJobsTotal} />
            <Kpi label="Batches actifs" value={kpis.batchJobsActive} />
            <Kpi label="IA aujourd'hui" value={kpis.aiRequestsToday} />
            <Kpi label="IA ce mois" value={kpis.aiRequestsThisMonth} />
          </section>

          <section className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 className="text-lg text-[var(--brand-ink)]">Documents recents</h2>
                <Link to="/documents" className="text-sm text-[var(--brand)] underline">
                  Voir tout
                </Link>
              </div>
              <ul className="space-y-2 text-sm">
                {(dash.data?.recentDocuments ?? []).length === 0 ? (
                  <li className="text-[var(--muted)]">Aucun document.</li>
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
                    <span className="text-xs text-[var(--muted)]">
                      {doc.status} · {new Date(doc.createdAt).toLocaleString("fr-FR")}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 className="text-lg text-[var(--brand-ink)]">Activite recente</h2>
                <Link to="/audit" className="text-sm text-[var(--brand)] underline">
                  Audit
                </Link>
              </div>
              <ul className="space-y-2 text-sm">
                {(dash.data?.recentActivity ?? []).length === 0 ? (
                  <li className="text-[var(--muted)]">Aucune activite.</li>
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
                    <span className="text-xs text-[var(--muted)]">
                      {row.status} · {new Date(row.createdAt).toLocaleString("fr-FR")}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] px-4 py-4">
      <p className="text-xs uppercase tracking-wide text-[var(--muted)]">{label}</p>
      <p className="mt-2 text-3xl text-[var(--brand-ink)]">{value}</p>
    </div>
  );
}
