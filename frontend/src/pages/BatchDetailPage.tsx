import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { downloadBatchZip, getBatch, getBatchErrors } from "@/api/batches";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

export function BatchDetailPage() {
  const { id = "" } = useParams();
  const { token } = useAuth();

  const job = useQuery({
    queryKey: ["batch", id],
    queryFn: () => getBatch(token!, id),
    enabled: !!token && !!id,
    refetchInterval: (q) => {
      const status = q.state.data?.status;
      return status === "COMPLETED" || status === "PARTIALLY_FAILED" || status === "FAILED"
        ? false
        : 2000;
    },
  });

  const errors = useQuery({
    queryKey: ["batch-errors", id],
    queryFn: () => getBatchErrors(token!, id),
    enabled: !!token && !!id && !!job.data?.errorsReady,
  });

  const data = job.data;

  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <AppHeader subtitle="Detail d'un job batch." />
      <p className="mb-4 text-sm">
        <Link to="/batches" className="text-[var(--brand)] underline">
          ← Batches
        </Link>
      </p>

      {job.isLoading ? <p>Chargement…</p> : null}
      {job.isError ? <p className="text-[var(--danger)]">Batch introuvable.</p> : null}

      {data ? (
        <div className="space-y-5">
          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-mono text-xs text-[var(--muted)]">{data.id}</p>
            <h1 className="mt-1 text-2xl text-[var(--brand-ink)]">{data.status}</h1>
            <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-[var(--muted)]">Total</dt>
                <dd>{data.totalItems}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Traites</dt>
                <dd>{data.processedItems}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Succes</dt>
                <dd>{data.successfulItems}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Echecs</dt>
                <dd>{data.failedItems}</dd>
              </div>
            </dl>
            {data.zipReady ? (
              <button
                type="button"
                className="mt-4 rounded-xl bg-[var(--brand)] px-4 py-2 text-sm text-white"
                onClick={() => void downloadBatchZip(token!, data.id)}
              >
                Telecharger ZIP
              </button>
            ) : null}
          </section>

          {data.errorsReady ? (
            <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
              <h2 className="text-lg text-[var(--brand-ink)]">Erreurs</h2>
              <ul className="mt-3 space-y-2 text-sm">
                {(errors.data ?? []).map((err) => (
                  <li key={err.rowNumber}>
                    Ligne {err.rowNumber}: {err.message}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
