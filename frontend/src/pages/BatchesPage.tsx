import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { createBatch, listBatches } from "@/api/batches";
import { listTemplates } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { CreatePanel } from "@/components/CreatePanel";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

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

  return (
    <AppShell
      title={t("batches.title")}
      description={t("batches.description")}
      width="wide"
      actions={
        <button
          type="button"
          onClick={() => setShowCreate((v) => !v)}
          className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
        >
          {showCreate ? t("batches.hide") : t("batches.new")}
        </button>
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
          <label className="block text-sm">
            {t("batches.template")}
            <select
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={templateId}
              onChange={(e) => setTemplateId(e.target.value)}
              aria-invalid={!!fieldErrors.templateId}
            >
              <option value="">{t("batches.choose")}</option>
              {(templates.data?.items ?? [])
                .filter((tpl) => tpl.status === "ACTIVE" && tpl.currentVersionId)
                .map((tpl) => (
                  <option key={tpl.id} value={tpl.id}>
                    {tpl.name} ({tpl.code})
                  </option>
                ))}
            </select>
            {fieldErrors.templateId ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.templateId}</p>
            ) : null}
          </label>
          <label className="block text-sm">
            {t("batches.csvFile")}
            <input
              type="file"
              accept=".csv,text/csv"
              className="mt-1 w-full text-sm"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              aria-invalid={!!fieldErrors.file}
            />
            {fieldErrors.file ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.file}</p>
            ) : null}
          </label>
          <p className="text-xs text-[var(--muted)]">
            {t("batches.csvHint")}
          </p>
          <button
            type="submit"
            disabled={upload.isPending}
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {upload.isPending ? t("batches.sending") : t("batches.launch")}
          </button>
        </form>
      </CreatePanel>

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      {batches.isLoading ? <p>{t("common.loading")}</p> : null}
      {batches.isError ? <p className="text-[var(--danger)]">{t("batches.loadError")}</p> : null}

      <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
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
              <tr key={job.id} className="border-b border-[var(--line)] last:border-0">
                <td className="px-4 py-3 font-mono text-xs text-[var(--muted)]">
                  {job.id.slice(0, 8)}…
                </td>
                <td className="px-4 py-3">
                  <StatusBadge
                    status={job.status}
                    label={t(`batches.statusLabel.${job.status}`, { defaultValue: job.status })}
                  />
                </td>
                <td className="px-4 py-3">
                  {t("batches.ok", { ok: job.successfulItems, total: job.totalItems })}
                  {job.failedItems > 0 ? (
                    <span className="text-[var(--danger)]">
                      {t("batches.err", { n: job.failedItems })}
                    </span>
                  ) : null}
                </td>
                <td className="px-4 py-3 whitespace-nowrap text-[var(--muted)]">
                  {new Date(job.createdAt).toLocaleString(loc)}
                </td>
                <td className="px-4 py-3">
                  <Link to={`/batches/${job.id}`} className="text-[var(--brand)] underline">
                    {t("batches.detail")}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!batches.isLoading && items.length === 0 ? (
        <p className="mt-6 text-[var(--muted)]">{t("batches.empty")}</p>
      ) : null}
    </AppShell>
  );
}
