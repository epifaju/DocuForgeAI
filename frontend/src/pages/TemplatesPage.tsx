import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { listTemplates } from "@/api/forms";
import {
  activateTemplate,
  archiveTemplate,
  createTemplate,
  uploadTemplateVersion,
} from "@/api/templates";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { CreatePanel } from "@/components/CreatePanel";
import { StatusBadge } from "@/components/StatusBadge";
import { isBlank, TEMPLATE_CODE_PATTERN } from "@/lib/formValidation";

export function TemplatesPage() {
  const { t } = useTranslation();
  const { token, canEditTemplates } = useAuth();
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("demo");
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [uploadFor, setUploadFor] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const pageSize = 10;

  const query = useQuery({
    queryKey: ["templates", page, pageSize],
    queryFn: () => listTemplates(token!, { page, size: pageSize }),
    enabled: !!token,
  });

  useEffect(() => {
    if (!query.data) return;
    if (query.data.totalPages > 0 && page >= query.data.totalPages) {
      setPage(Math.max(0, query.data.totalPages - 1));
    }
  }, [query.data, page]);

  const create = useMutation({
    mutationFn: () =>
      createTemplate(token!, {
        code: code.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
        category: category.trim() || undefined,
      }),
    onSuccess: (tpl) => {
      setMessage(t("templates.created", { code: tpl.code }));
      setCode("");
      setName("");
      setDescription("");
      setShowCreate(false);
      setPage(0);
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const upload = useMutation({
    mutationFn: async ({ id, file }: { id: string; file: File }) => {
      await uploadTemplateVersion(token!, id, file, true);
      return id;
    },
    onSuccess: () => {
      setMessage(t("templates.uploaded"));
      setUploadFor(null);
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const activate = useMutation({
    mutationFn: (id: string) => activateTemplate(token!, id),
    onSuccess: (tpl) => {
      setMessage(t("templates.activated", { code: tpl.code }));
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const archive = useMutation({
    mutationFn: (id: string) => archiveTemplate(token!, id),
    onSuccess: (tpl) => {
      setMessage(t("templates.archived", { code: tpl.code }));
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  function onCreate(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    const trimmedCode = code.trim();
    if (isBlank(trimmedCode)) next.code = t("validation.required");
    else if (!TEMPLATE_CODE_PATTERN.test(trimmedCode)) next.code = t("validation.templateCode");
    if (isBlank(name)) next.name = t("validation.required");
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;
    setMessage(null);
    create.mutate();
  }

  const items = query.data?.items ?? [];

  return (
    <AppShell
      title={t("templates.title")}
      description={t("templates.description", { placeholders: "{{variables}}" })}
      width="wide"
      actions={
        canEditTemplates ? (
          <button
            type="button"
            onClick={() => setShowCreate((v) => !v)}
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
          >
            {showCreate ? t("templates.hide") : t("templates.new")}
          </button>
        ) : null
      }
    >
      {!canEditTemplates ? (
        <p className="mb-4 text-sm text-[var(--muted)]">{t("templates.readOnly")}</p>
      ) : null}

      <CreatePanel
        open={showCreate}
        title={t("templates.panelTitle")}
        onClose={() => setShowCreate(false)}
      >
        <form onSubmit={onCreate} noValidate className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            {t("templates.code")}
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="lettre_simple"
              aria-invalid={!!fieldErrors.code}
            />
            {fieldErrors.code ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.code}</p>
            ) : null}
          </label>
          <label className="block text-sm">
            {t("templates.name")}
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={name}
              onChange={(e) => setName(e.target.value)}
              aria-invalid={!!fieldErrors.name}
            />
            {fieldErrors.name ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.name}</p>
            ) : null}
          </label>
          <label className="block text-sm">
            {t("templates.category")}
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            />
          </label>
          <label className="block text-sm sm:col-span-2">
            {t("templates.descriptionLabel")}
            <textarea
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={create.isPending}
              className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
            >
              {create.isPending ? t("common.creating") : t("common.create")}
            </button>
          </div>
        </form>
      </CreatePanel>

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">{t("templates.loadError")}</p> : null}

      <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-[var(--line)] text-[var(--muted)]">
            <tr>
              <th className="px-4 py-3 font-medium">{t("templates.colName")}</th>
              <th className="px-4 py-3 font-medium">{t("templates.colCode")}</th>
              <th className="px-4 py-3 font-medium">{t("templates.colStatus")}</th>
              <th className="px-4 py-3 font-medium">{t("templates.colVersion")}</th>
              <th className="px-4 py-3 font-medium">{t("templates.colActions")}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((tpl) => (
              <tr key={tpl.id} className="border-b border-[var(--line)] align-top last:border-0">
                <td className="px-4 py-3 font-medium">{tpl.name}</td>
                <td className="px-4 py-3 font-mono text-xs text-[var(--muted)]">{tpl.code}</td>
                <td className="px-4 py-3">
                  <StatusBadge
                    status={tpl.status}
                    label={t(`templates.statusLabel.${tpl.status}`, { defaultValue: tpl.status })}
                  />
                </td>
                <td className="px-4 py-3 text-[var(--muted)]">
                  {tpl.currentVersionNumber != null ? `v${tpl.currentVersionNumber}` : "—"}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    {tpl.currentVersionId ? (
                      <Link to={`/forms/${tpl.currentVersionId}`} className="text-[var(--brand)] underline">
                        {t("templates.form")}
                      </Link>
                    ) : (
                      <span className="text-[var(--muted)]">{t("templates.noVersion")}</span>
                    )}
                    {canEditTemplates ? (
                      <>
                        <button
                          type="button"
                          className="text-[var(--brand)] underline"
                          onClick={() => setUploadFor(uploadFor === tpl.id ? null : tpl.id)}
                        >
                          {t("templates.import")}
                        </button>
                        {tpl.status !== "ACTIVE" && tpl.currentVersionId ? (
                          <button
                            type="button"
                            className="text-[var(--brand)] underline disabled:opacity-40"
                            disabled={activate.isPending}
                            onClick={() => activate.mutate(tpl.id)}
                          >
                            {t("templates.activate")}
                          </button>
                        ) : null}
                        {tpl.status === "ACTIVE" ? (
                          <button
                            type="button"
                            className="text-[var(--muted)] underline disabled:opacity-40"
                            disabled={archive.isPending}
                            onClick={() => archive.mutate(tpl.id)}
                          >
                            {t("templates.archive")}
                          </button>
                        ) : null}
                      </>
                    ) : null}
                  </div>
                  {uploadFor === tpl.id ? (
                    <label className="mt-2 block text-xs text-[var(--muted)]">
                      {t("templates.docxFile")}
                      <input
                        type="file"
                        accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        className="mt-1 block w-full max-w-xs text-sm"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) upload.mutate({ id: tpl.id, file });
                        }}
                      />
                    </label>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!query.isLoading && items.length === 0 ? (
        <p className="mt-6 text-[var(--muted)]">
          {t("templates.empty")}{" "}
          {canEditTemplates ? t("templates.emptyHintEdit") : t("templates.emptyHintView")}
        </p>
      ) : null}

      {query.data && query.data.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-40"
          >
            {t("common.previous")}
          </button>
          <span className="text-[var(--muted)]">
            {t("common.pageOf", {
              current: query.data.page + 1,
              total: query.data.totalPages,
              count: query.data.totalElements,
            })}
          </span>
          <button
            type="button"
            disabled={page + 1 >= query.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-40"
          >
            {t("common.next")}
          </button>
        </div>
      ) : null}
    </AppShell>
  );
}
