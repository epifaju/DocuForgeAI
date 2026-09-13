import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Download, FileSpreadsheet, FileText } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import {
  type BatchJob,
  createBatch,
  downloadBatchErrors,
  listBatches,
} from "@/api/batches";
import { listTemplates } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { CardList, CardListItem } from "@/components/CardList";
import { CreatePanel } from "@/components/CreatePanel";
import { StatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Select } from "@/components/ui/Select";
import { dateLocale } from "@/i18n";
import { cn } from "@/lib/cn";
import { formatRelative } from "@/lib/formatRelative";

const pillClass =
  "inline-flex min-h-9 items-center gap-1.5 rounded-full border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-xs font-medium text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]/40 hover:bg-[var(--brand-soft)]";

const pillDangerClass =
  "inline-flex min-h-9 items-center gap-1.5 rounded-full border border-[var(--danger)]/30 bg-[var(--danger-soft)] px-3 py-1.5 text-xs font-medium text-[var(--danger)] transition-colors hover:border-[var(--danger)]/50";

function truncateId(id: string) {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

function hasErrorReport(job: BatchJob) {
  return (
    job.errorsReady &&
    (job.failedItems > 0 || job.status === "FAILED" || job.status === "PARTIALLY_FAILED")
  );
}

function BatchProgress({ job }: { job: BatchJob }) {
  const { t } = useTranslation();
  const total = Math.max(job.totalItems, 1);
  const ratio = Math.min(1, Math.max(0, job.processedItems / total));
  const failed =
    job.failedItems > 0 || job.status === "FAILED" || job.status === "PARTIALLY_FAILED";
  const barColor = failed ? "bg-[var(--danger)]" : "bg-[var(--brand)]";
  // Failed with 0 success: show empty track (mock). Otherwise fill by processed ratio.
  const widthPct =
    failed && job.successfulItems === 0 ? 0 : Math.round(ratio * 100);

  return (
    <div className="min-w-[9rem] max-w-[14rem]">
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--line)]/70">
        <div className={cn("h-full rounded-full transition-all", barColor)} style={{ width: `${widthPct}%` }} />
      </div>
      <p className={cn("mt-1.5 text-xs", failed ? "text-[var(--danger)]" : "text-[var(--muted)]")}>
        {t("batches.progressOk", { ok: job.successfulItems, total: job.totalItems })}
        {job.failedItems > 0 ? t("batches.progressErr", { n: job.failedItems }) : null}
      </p>
    </div>
  );
}

function BatchActions({
  job,
  token,
  detailLabel,
  errorsLabel,
}: {
  job: BatchJob;
  token: string;
  detailLabel: string;
  errorsLabel: string;
}) {
  const showErrors = hasErrorReport(job);

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <Link to={`/batches/${job.id}`} className={pillClass}>
        <FileText className="h-3.5 w-3.5" aria-hidden />
        {detailLabel}
      </Link>
      {showErrors ? (
        <button
          type="button"
          className={pillDangerClass}
          onClick={() => void downloadBatchErrors(token, job.id)}
        >
          <Download className="h-3.5 w-3.5" aria-hidden />
          {errorsLabel}
        </button>
      ) : null}
    </div>
  );
}

export function BatchesPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const loc = dateLocale(i18n.language);
  const [showCreate, setShowCreate] = useState(false);
  const [templateId, setTemplateId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const batches = useQuery({
    queryKey: ["batches"],
    queryFn: () => listBatches(token!),
    enabled: !!token,
    refetchInterval: 3000,
  });

  const templates = useQuery({
    queryKey: ["templates"],
    queryFn: () => listTemplates(token!),
    enabled: !!token,
  });

  const upload = useMutation({
    mutationFn: async () => {
      if (!token || !file || !templateId) throw new Error(t("batches.needFile"));
      return createBatch(token, file, templateId);
    },
    onSuccess: (job) => {
      setMessage(t("batches.accepted", { id: job.id.slice(0, 8), status: job.status }));
      setFile(null);
      setTemplateId("");
      setShowCreate(false);
      void queryClient.invalidateQueries({ queryKey: ["batches"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const items = batches.data?.items ?? [];
  const totalElements = batches.data?.totalElements ?? items.length;

  return (
    <AppShell
      title={t("batches.title")}
      description={t("batches.description")}
      width="wide"
      actions={
        <Button type="button" onClick={() => setShowCreate((v) => !v)}>
          <FileSpreadsheet className="h-4 w-4" aria-hidden />
          {showCreate ? t("batches.hide") : t("batches.new")}
        </Button>
      }
    >
      <CreatePanel
        open={showCreate}
        title={t("batches.panelTitle")}
        onClose={() => setShowCreate(false)}
      >
        <form
          className="space-y-3"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            const next: Record<string, string> = {};
            if (!templateId) next.templateId = t("validation.required");
            if (!file) next.file = t("validation.required");
            setFieldErrors(next);
            if (Object.keys(next).length > 0) return;
            setMessage(null);
            upload.mutate();
          }}
        >
          <Field label={t("batches.template")} error={fieldErrors.templateId}>
            <Select
              value={templateId}
              onChange={(e) => setTemplateId(e.target.value)}
              invalid={!!fieldErrors.templateId}
            >
              <option value="">{t("batches.choose")}</option>
              {(templates.data?.items ?? [])
                .filter((tpl) => tpl.status === "ACTIVE" && tpl.currentVersionId)
                .map((tpl) => (
                  <option key={tpl.id} value={tpl.id}>
                    {tpl.name} ({tpl.code})
                  </option>
                ))}
            </Select>
          </Field>
          <Field label={t("batches.csvFile")} error={fieldErrors.file}>
            <input
              type="file"
              accept=".csv,text/csv"
              className="block w-full text-sm"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              aria-invalid={!!fieldErrors.file}
            />
          </Field>
          <p className="text-xs text-[var(--muted)]">{t("batches.csvHint")}</p>
          <Button type="submit" disabled={upload.isPending}>
            {upload.isPending ? t("batches.sending") : t("batches.launch")}
          </Button>
        </form>
      </CreatePanel>

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      {batches.isLoading ? <p>{t("common.loading")}</p> : null}
      {batches.isError ? <p className="text-[var(--danger)]">{t("batches.loadError")}</p> : null}

      <CardList>
        {items.map((job) => (
          <CardListItem
            key={job.id}
            className={cn(
              (job.status === "FAILED" || job.status === "PARTIALLY_FAILED") &&
                "border-[var(--danger)]/25 bg-[var(--danger-soft)]/35",
            )}
          >
            <div className="flex flex-wrap items-start justify-between gap-2">
              <p className="font-mono text-xs text-[var(--muted)]" title={job.id}>
                {truncateId(job.id)}
              </p>
              <StatusBadge
                status={job.status}
                label={t(`batches.statusLabel.${job.status}`, { defaultValue: job.status })}
              />
            </div>
            <div className="mt-3">
              <BatchProgress job={job} />
            </div>
            <p className="mt-2 text-sm text-[var(--muted)]">
              <time dateTime={job.createdAt} title={new Date(job.createdAt).toLocaleString(loc)}>
                {formatRelative(job.createdAt, loc)}
              </time>
            </p>
            {token ? (
              <div className="mt-3 border-t border-[var(--line)] pt-2">
                <BatchActions
                  job={job}
                  token={token}
                  detailLabel={t("batches.detail")}
                  errorsLabel={t("batches.errorReport")}
                />
              </div>
            ) : null}
          </CardListItem>
        ))}
      </CardList>

      <div className="hidden overflow-x-auto rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)] md:block">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-[var(--line)] text-[var(--muted)]">
            <tr>
              <th className="px-4 py-3 font-medium">{t("batches.colId")}</th>
              <th className="px-4 py-3 font-medium">{t("batches.colStatus")}</th>
              <th className="px-4 py-3 font-medium">{t("batches.colProgress")}</th>
              <th className="px-4 py-3 font-medium">{t("batches.colCreated")}</th>
              <th className="px-4 py-3 font-medium">{t("batches.colActions")}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((job) => (
              <tr
                key={job.id}
                className={cn(
                  "border-b border-[var(--line)] align-middle last:border-0",
                  job.status === "FAILED" && "bg-[var(--danger-soft)]/40",
                )}
              >
                <td className="px-4 py-3 font-mono text-xs text-[var(--muted)]" title={job.id}>
                  {truncateId(job.id)}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge
                    status={job.status}
                    label={t(`batches.statusLabel.${job.status}`, { defaultValue: job.status })}
                  />
                </td>
                <td className="px-4 py-3">
                  <BatchProgress job={job} />
                </td>
                <td className="px-4 py-3 whitespace-nowrap text-[var(--muted)]">
                  <time dateTime={job.createdAt} title={new Date(job.createdAt).toLocaleString(loc)}>
                    {formatRelative(job.createdAt, loc)}
                  </time>
                </td>
                <td className="px-4 py-3">
                  {token ? (
                    <BatchActions
                      job={job}
                      token={token}
                      detailLabel={t("batches.detail")}
                      errorsLabel={t("batches.errorReport")}
                    />
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!batches.isLoading && items.length === 0 ? (
        <p className="mt-6 text-[var(--muted)]">{t("batches.empty")}</p>
      ) : null}

      {!batches.isLoading && items.length > 0 ? (
        <p className="mt-3 text-sm text-[var(--muted)]">
          {t("batches.countShown", { shown: items.length, total: totalElements })}
        </p>
      ) : null}
    </AppShell>
  );
}
