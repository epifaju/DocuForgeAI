import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { downloadBatchZip, getBatch, getBatchErrors } from "@/api/batches";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { StatusBadge } from "@/components/StatusBadge";

export function BatchDetailPage() {
  const { t } = useTranslation();
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
    <AppShell
      title={t("batchDetail.title")}
      description={t("batchDetail.description")}
      width="narrow"
      actions={
        <Link to="/batches" className="text-sm text-[var(--brand)] underline">
          {t("batchDetail.back")}
        </Link>
      }
    >
      {job.isLoading ? <p>{t("common.loading")}</p> : null}
      {job.isError ? <p className="text-[var(--danger)]">{t("batchDetail.notFound")}</p> : null}

      {data ? (
        <div className="space-y-5">
          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-mono text-xs text-[var(--muted)]">{data.id}</p>
            <div className="mt-2">
              <StatusBadge
                status={data.status}
                label={t(`batches.statusLabel.${data.status}`, { defaultValue: data.status })}
              />
            </div>
            <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-[var(--muted)]">{t("batchDetail.total")}</dt>
                <dd>{data.totalItems}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("batchDetail.processed")}</dt>
                <dd>{data.processedItems}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("batchDetail.success")}</dt>
                <dd>{data.successfulItems}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("batchDetail.failed")}</dt>
                <dd>{data.failedItems}</dd>
              </div>
            </dl>
            {data.zipReady ? (
              <button
                type="button"
                className="mt-4 rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
                onClick={() => void downloadBatchZip(token!, data.id)}
              >
                {t("batchDetail.downloadZip")}
              </button>
            ) : null}
          </section>

          {data.errorsReady ? (
            <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
              <h2 className="text-lg text-[var(--brand-ink)]">{t("batchDetail.errors")}</h2>
              <ul className="mt-3 space-y-2 text-sm">
                {(errors.data ?? []).map((err) => (
                  <li key={err.rowNumber}>
                    {t("batchDetail.rowError", { row: err.rowNumber, message: err.message })}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      ) : null}
    </AppShell>
  );
}
