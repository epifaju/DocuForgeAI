import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import {
  getPackImport,
  getPackUpdatePreview,
  installPackImport,
  type PackInstallResult,
  type PackValidationIssue,
  uploadPackImport,
  validatePackImport,
} from "@/api/businessPacks";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { PackUpdatePreviewPanel } from "@/components/PackUpdatePreviewPanel";
import { StatusBadge } from "@/components/StatusBadge";

type WizardStep = "upload" | "validation" | "review" | "install" | "success";

const STEPS: WizardStep[] = ["upload", "validation", "review", "install", "success"];

function issueLabel(issue: PackValidationIssue, t: (key: string) => string): string {
  if (issue.message?.startsWith("error.")) {
    const localized = t(issue.message);
    if (localized !== issue.message) return localized;
  }
  return issue.message || issue.code;
}

function Stepper({ current }: { current: WizardStep }) {
  const { t } = useTranslation();
  const index = STEPS.indexOf(current);
  return (
    <ol className="mb-8 flex flex-wrap gap-2 text-xs sm:text-sm">
      {STEPS.map((step, i) => {
        const done = i < index;
        const active = i === index;
        return (
          <li
            key={step}
            className={[
              "rounded-xl px-3 py-1.5",
              active
                ? "bg-[var(--bg-accent)] font-medium text-[var(--brand-ink)]"
                : done
                  ? "text-[var(--brand-ink)]"
                  : "text-[var(--muted)]",
            ].join(" ")}
          >
            {i + 1}. {t(`packs.import.step.${step}`)}
          </li>
        );
      })}
    </ol>
  );
}

export function BusinessPackImportPage() {
  const { t } = useTranslation();
  const { token, isAdmin } = useAuth();
  const { jobId } = useParams<{ jobId?: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [enablePack, setEnablePack] = useState(true);
  const [enableTemplates, setEnableTemplates] = useState(true);
  const [confirmInstall, setConfirmInstall] = useState(false);
  const [installResult, setInstallResult] = useState<PackInstallResult | null>(null);
  const [autoValidated, setAutoValidated] = useState(false);

  const jobQuery = useQuery({
    queryKey: ["pack-import", jobId],
    queryFn: () => getPackImport(token!, jobId!),
    enabled: !!token && isAdmin && !!jobId,
  });

  const job = jobQuery.data;

  const updatePreviewQuery = useQuery({
    queryKey: ["pack-update-preview", jobId],
    queryFn: () => getPackUpdatePreview(token!, jobId!),
    enabled: !!token && isAdmin && !!jobId && job?.status === "VALID",
  });

  const updatePreview = updatePreviewQuery.data;
  const isUpdate = !!updatePreview?.updateCandidate;

  const step: WizardStep = useMemo(() => {
    if (installResult) return "success";
    if (!jobId) return "upload";
    if (!job) return "validation";
    if (job.status === "INSTALLED") return "success";
    if (job.status === "VALID") return "review";
    if (job.status === "INVALID" || job.status === "FAILED" || job.status === "EXPIRED") {
      return "validation";
    }
    if (job.status === "INSTALLING") return "install";
    return "validation";
  }, [jobId, job, installResult]);

  const upload = useMutation({
    mutationFn: async () => {
      if (!token || !file) throw new Error(t("packs.import.needFile"));
      if (!file.name.toLowerCase().endsWith(".zip")) {
        throw new Error(t("packs.import.zipOnly"));
      }
      return uploadPackImport(token, file);
    },
    onSuccess: (uploaded) => {
      setMessage(null);
      setAutoValidated(false);
      void navigate(`/business-packs/import/${uploaded.jobId}`, { replace: true });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const validate = useMutation({
    mutationFn: () => validatePackImport(token!, jobId!),
    onSuccess: (validated) => {
      setMessage(null);
      void queryClient.setQueryData(["pack-import", jobId], validated);
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const install = useMutation({
    mutationFn: () =>
      installPackImport(token!, jobId!, {
        enablePack,
        enableTemplates,
      }),
    onSuccess: (result) => {
      setConfirmInstall(false);
      setInstallResult(result);
      setMessage(null);
      void queryClient.invalidateQueries({ queryKey: ["business-packs"] });
      void queryClient.invalidateQueries({ queryKey: ["business-pack"] });
      void queryClient.invalidateQueries({ queryKey: ["pack-import", jobId] });
      void queryClient.invalidateQueries({ queryKey: ["pack-update-preview", jobId] });
    },
    onError: (err: Error) => {
      setConfirmInstall(false);
      setMessage(err.message);
      void jobQuery.refetch();
    },
  });

  useEffect(() => {
    if (!job || !jobId || autoValidated || validate.isPending) return;
    if (job.status === "UPLOADED") {
      setAutoValidated(true);
      validate.mutate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once per UPLOADED job
  }, [job?.status, jobId, autoValidated]);

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  function acceptFile(next: File | null) {
    if (!next) {
      setFile(null);
      return;
    }
    if (!next.name.toLowerCase().endsWith(".zip")) {
      setMessage(t("packs.import.zipOnly"));
      setFile(null);
      return;
    }
    setMessage(null);
    setFile(next);
  }

  const report = job?.validationReport;
  const issues = report?.issues ?? [];
  const errors = issues.filter((i) => i.severity === "ERROR");
  const warnings = issues.filter((i) => i.severity === "WARNING");

  return (
    <AppShell
      title={t("packs.import.title")}
      description={t("packs.import.description")}
      width="form"
      actions={
        <Link
          to="/business-packs"
          className="rounded-xl border border-[var(--line)] bg-[var(--surface)] px-3.5 py-2 text-sm text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]"
        >
          {t("packs.back")}
        </Link>
      }
    >
      <Stepper current={step} />

      {message ? <p className="mb-4 text-sm text-[var(--danger)]">{message}</p> : null}

      {step === "upload" ? (
        <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
          <p className="text-sm text-[var(--muted)]">{t("packs.import.uploadHint")}</p>
          <div
            className={[
              "mt-4 rounded-2xl border border-dashed px-4 py-10 text-center transition-colors",
              dragOver
                ? "border-[var(--brand)] bg-[var(--bg-accent)]"
                : "border-[var(--line)] bg-white",
            ].join(" ")}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              const dropped = e.dataTransfer.files?.[0] ?? null;
              acceptFile(dropped);
            }}
          >
            <p className="text-sm text-[var(--brand-ink)]">{t("packs.import.dropzone")}</p>
            <p className="mt-2 text-xs text-[var(--muted)]">{t("packs.import.formatRules")}</p>
            <button
              type="button"
              className="mt-4 rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
              onClick={() => fileInputRef.current?.click()}
            >
              {t("packs.import.browse")}
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".zip,application/zip"
              className="hidden"
              onChange={(e) => acceptFile(e.target.files?.[0] ?? null)}
            />
            {file ? (
              <p className="mt-4 text-sm text-[var(--brand-ink)]">
                {file.name}{" "}
                <span className="text-[var(--muted)]">
                  ({Math.max(1, Math.round(file.size / 1024))} Ko)
                </span>
              </p>
            ) : null}
          </div>
          <div className="mt-5 flex justify-end">
            <button
              type="button"
              className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              disabled={!file || upload.isPending}
              onClick={() => upload.mutate()}
            >
              {upload.isPending ? t("packs.import.uploading") : t("packs.import.uploadAction")}
            </button>
          </div>
        </section>
      ) : null}

      {step === "validation" && jobId ? (
        <section className="space-y-4">
          {jobQuery.isLoading || validate.isPending ? (
            <p>{t("packs.import.validating")}</p>
          ) : null}

          {job ? (
            <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-sm text-[var(--muted)]">{job.originalFilename}</p>
                  <p className="mt-1 font-medium text-[var(--brand-ink)]">
                    {job.detectedPackKey || t("packs.import.detecting")}
                    {job.detectedVersion ? ` · ${job.detectedVersion}` : ""}
                  </p>
                </div>
                <StatusBadge status={job.status} label={t(`packs.import.status.${job.status}`)} />
              </div>

              {job.status === "INVALID" || errors.length > 0 ? (
                <div className="mt-5">
                  <h2 className="text-sm font-semibold text-[var(--danger)]">
                    {t("packs.import.errorsTitle", { count: errors.length || report?.summary?.errors || 0 })}
                  </h2>
                  <ul className="mt-3 space-y-2 text-sm">
                    {(errors.length ? errors : issues).map((issue, idx) => (
                      <li
                        key={`${issue.code}-${idx}`}
                        className="rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                      >
                        <span className="font-medium text-[var(--danger)]">✗ {issue.code}</span>
                        {issue.file ? (
                          <span className="ml-2 text-[var(--muted)]">{issue.file}</span>
                        ) : null}
                        <p className="mt-1 text-[var(--brand-ink)]">{issueLabel(issue, t)}</p>
                        {issue.variable ? (
                          <p className="mt-1 text-xs text-[var(--muted)]">
                            {`{{${issue.variable}}}`}
                          </p>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}

              {warnings.length > 0 ? (
                <div className="mt-5">
                  <h2 className="text-sm font-semibold text-[var(--brand-ink)]">
                    {t("packs.import.warningsTitle", { count: warnings.length })}
                  </h2>
                  <ul className="mt-3 space-y-2 text-sm text-[var(--muted)]">
                    {warnings.map((issue, idx) => (
                      <li key={`${issue.code}-w-${idx}`}>
                        ⚠ {issue.file ? `${issue.file} — ` : ""}
                        {issueLabel(issue, t)}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}

              {job.status === "UPLOADED" || job.status === "INVALID" || job.status === "FAILED" ? (
                <div className="mt-5 flex flex-wrap gap-2">
                  {job.status === "UPLOADED" ? (
                    <button
                      type="button"
                      className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                      disabled={validate.isPending}
                      onClick={() => validate.mutate()}
                    >
                      {t("packs.import.retryValidate")}
                    </button>
                  ) : null}
                  <Link
                    to="/business-packs/import"
                    className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
                    onClick={() => {
                      setFile(null);
                      setAutoValidated(false);
                      setInstallResult(null);
                    }}
                  >
                    {t("packs.import.startOver")}
                  </Link>
                </div>
              ) : null}
            </div>
          ) : null}

          {jobQuery.isError ? (
            <p className="text-[var(--danger)]">{t("packs.import.jobNotFound")}</p>
          ) : null}
        </section>
      ) : null}

      {step === "review" && job ? (
        <section className="space-y-4">
          {updatePreviewQuery.isLoading ? (
            <p className="text-sm text-[var(--muted)]">{t("packs.import.update.loading")}</p>
          ) : null}

          {isUpdate && updatePreview ? <PackUpdatePreviewPanel preview={updatePreview} /> : null}

          <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
            <h2 className="text-lg text-[var(--brand-ink)]">
              {report?.pack?.name || job.detectedPackKey || t("packs.import.unknownPack")}
            </h2>
            {!isUpdate ? (
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-[var(--muted)]">{t("packs.packKey")}</dt>
                  <dd className="mt-0.5 break-all">{report?.pack?.id || job.detectedPackKey}</dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">{t("packs.colVersion")}</dt>
                  <dd className="mt-0.5 tabular-nums">
                    {report?.pack?.version || job.detectedVersion || "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">{t("packs.import.templatesCount")}</dt>
                  <dd className="mt-0.5">{report?.summary?.templates ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">{t("packs.import.promptsCount")}</dt>
                  <dd className="mt-0.5">{report?.summary?.prompts ?? "—"}</dd>
                </div>
              </dl>
            ) : (
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-[var(--muted)]">{t("packs.packKey")}</dt>
                  <dd className="mt-0.5 break-all">
                    {updatePreview?.packKey || report?.pack?.id || job.detectedPackKey}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--muted)]">{t("packs.import.templatesCount")}</dt>
                  <dd className="mt-0.5">{report?.summary?.templates ?? "—"}</dd>
                </div>
              </dl>
            )}
            {warnings.length > 0 ? (
              <p className="mt-4 text-sm text-[var(--muted)]">
                {t("packs.import.warningsContinue", { count: warnings.length })}
              </p>
            ) : isUpdate ? (
              <p className="mt-4 text-sm text-[var(--muted)]">{t("packs.import.update.ready")}</p>
            ) : (
              <p className="mt-4 text-sm text-[var(--muted)]">{t("packs.import.ready")}</p>
            )}
          </div>

          <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
            <h3 className="text-sm font-semibold text-[var(--brand-ink)]">
              {t("packs.import.options")}
            </h3>
            <label className="mt-3 flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={enablePack}
                onChange={(e) => setEnablePack(e.target.checked)}
              />
              {t("packs.import.enablePack")}
            </label>
            <label className="mt-2 flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={enableTemplates}
                onChange={(e) => setEnableTemplates(e.target.checked)}
              />
              {t("packs.import.enableTemplates")}
            </label>
            <div className="mt-5 flex justify-end">
              <button
                type="button"
                className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
                onClick={() => setConfirmInstall(true)}
              >
                {isUpdate ? t("packs.import.update.action") : t("packs.import.installAction")}
              </button>
            </div>
          </div>
        </section>
      ) : null}

      {step === "install" ? <p>{t("packs.import.installing")}</p> : null}

      {step === "success" ? (
        <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
          <h2 className="text-lg text-[var(--brand-ink)]">
            {installResult?.installationType === "UPDATE"
              ? t("packs.import.update.successTitle")
              : t("packs.import.successTitle")}
          </h2>
          <p className="mt-2 text-sm text-[var(--muted)]">
            {installResult
              ? installResult.installationType === "UPDATE"
                ? t("packs.import.update.successBody", {
                    packKey: installResult.packKey,
                    version: installResult.version,
                    templates: installResult.templatesInstalled,
                    prompts: installResult.promptsInstalled,
                  })
                : t("packs.import.successBody", {
                    packKey: installResult.packKey,
                    version: installResult.version,
                    templates: installResult.templatesInstalled,
                    prompts: installResult.promptsInstalled,
                  })
              : t("packs.import.successAlready")}
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            {installResult?.packId ? (
              <Link
                to={`/business-packs/${installResult.packId}`}
                className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
              >
                {t("packs.import.viewPack")}
              </Link>
            ) : null}
            <Link
              to="/business-packs"
              className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
            >
              {t("packs.import.backToList")}
            </Link>
            <Link
              to="/templates"
              className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
            >
              {t("packs.import.viewTemplates")}
            </Link>
          </div>
        </section>
      ) : null}

      <ConfirmDialog
        open={confirmInstall}
        title={
          isUpdate ? t("packs.import.update.confirmTitle") : t("packs.import.confirmTitle")
        }
        body={
          isUpdate
            ? t(
                (updatePreview?.breakingChanges?.length ?? 0) > 0
                  ? "packs.import.update.confirmBodyBreaking"
                  : "packs.import.update.confirmBody",
                {
                  name: report?.pack?.name || job?.detectedPackKey || "",
                  from: updatePreview?.installedVersion || "",
                  to: updatePreview?.candidateVersion || report?.pack?.version || "",
                },
              )
            : t("packs.import.confirmBody", {
                name: report?.pack?.name || job?.detectedPackKey || "",
                version: report?.pack?.version || job?.detectedVersion || "",
              })
        }
        confirmLabel={
          isUpdate ? t("packs.import.update.action") : t("packs.import.installAction")
        }
        cancelLabel={t("common.cancel")}
        pending={install.isPending}
        danger={isUpdate && (updatePreview?.breakingChanges?.length ?? 0) > 0}
        onConfirm={() => install.mutate()}
        onCancel={() => setConfirmInstall(false)}
      />
    </AppShell>
  );
}
