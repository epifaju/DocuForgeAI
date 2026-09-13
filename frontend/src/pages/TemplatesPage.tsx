import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Archive,
  FilePlus,
  FileText,
  RotateCw,
  Search,
  Upload,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { listTemplates } from "@/api/forms";
import {
  activateTemplate,
  archiveTemplate,
  createTemplate,
  uploadTemplateVersion,
} from "@/api/templates";
import type { TemplateSummary } from "@/api/types";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { CardList, CardListItem } from "@/components/CardList";
import { CreatePanel } from "@/components/CreatePanel";
import { IconMenu, IconMenuItem } from "@/components/IconMenu";
import { StatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Textarea } from "@/components/ui/Textarea";
import { cn } from "@/lib/cn";
import { isBlank, TEMPLATE_CODE_PATTERN } from "@/lib/formValidation";

const STATUSES = ["", "DRAFT", "ACTIVE", "ARCHIVED"] as const;

const pillClass =
  "inline-flex min-h-9 items-center gap-1.5 rounded-full border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-xs font-medium text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]/40 hover:bg-[var(--brand-soft)] disabled:opacity-40";

function parseStatusParam(raw: string | null): string {
  if (!raw) return "";
  return (STATUSES as readonly string[]).includes(raw) ? raw : "";
}

function useDebouncedValue<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(value), ms);
    return () => window.clearTimeout(id);
  }, [value, ms]);
  return debounced;
}

function TertiaryAction({
  tpl,
  canEdit,
  activatePending,
  archivePending,
  onActivate,
  onArchive,
  activateLabel,
  archiveLabel,
  compact,
}: {
  tpl: TemplateSummary;
  canEdit: boolean;
  activatePending: boolean;
  archivePending: boolean;
  onActivate: (id: string) => void;
  onArchive: (id: string) => void;
  activateLabel: string;
  archiveLabel: string;
  /** When true, render as menu item content only (caller wraps IconMenu). */
  compact?: "button" | "menu";
}) {
  if (!canEdit) return null;

  if (tpl.status === "ACTIVE") {
    if (compact === "menu") {
      return (
        <IconMenuItem disabled={archivePending} onClick={() => onArchive(tpl.id)}>
          <Archive className="h-3.5 w-3.5" aria-hidden />
          {archiveLabel}
        </IconMenuItem>
      );
    }
    return (
      <button
        type="button"
        className={pillClass}
        disabled={archivePending}
        onClick={() => onArchive(tpl.id)}
      >
        <Archive className="h-3.5 w-3.5" aria-hidden />
        {archiveLabel}
      </button>
    );
  }

  if (tpl.currentVersionId) {
    if (compact === "menu") {
      return (
        <IconMenuItem disabled={activatePending} onClick={() => onActivate(tpl.id)}>
          <RotateCw className="h-3.5 w-3.5" aria-hidden />
          {activateLabel}
        </IconMenuItem>
      );
    }
    return (
      <button
        type="button"
        className={pillClass}
        disabled={activatePending}
        onClick={() => onActivate(tpl.id)}
      >
        <RotateCw className="h-3.5 w-3.5" aria-hidden />
        {activateLabel}
      </button>
    );
  }

  return null;
}

function TemplateActions({
  tpl,
  canEdit,
  uploadFor,
  setUploadFor,
  activatePending,
  archivePending,
  onActivate,
  onArchive,
  onUpload,
  formLabel,
  noVersionLabel,
  importLabel,
  activateLabel,
  archiveLabel,
  docxLabel,
  moreActionsLabel,
  layout,
}: {
  tpl: TemplateSummary;
  canEdit: boolean;
  uploadFor: string | null;
  setUploadFor: (id: string | null) => void;
  activatePending: boolean;
  archivePending: boolean;
  onActivate: (id: string) => void;
  onArchive: (id: string) => void;
  onUpload: (id: string, file: File) => void;
  formLabel: string;
  noVersionLabel: string;
  importLabel: string;
  activateLabel: string;
  archiveLabel: string;
  docxLabel: string;
  moreActionsLabel: string;
  /** table: collapse 3rd action under lg:; card: show all pills */
  layout: "table" | "card";
}) {
  const hasTertiary =
    canEdit &&
    (tpl.status === "ACTIVE" || (tpl.status !== "ACTIVE" && !!tpl.currentVersionId));

  const formControl = tpl.currentVersionId ? (
    <Link to={`/forms/${tpl.currentVersionId}`} className={pillClass}>
      <FileText className="h-3.5 w-3.5" aria-hidden />
      {formLabel}
    </Link>
  ) : (
    <span className="px-2 py-1.5 text-xs text-[var(--muted)]">{noVersionLabel}</span>
  );

  const importControl = canEdit ? (
    <button
      type="button"
      className={pillClass}
      onClick={() => setUploadFor(uploadFor === tpl.id ? null : tpl.id)}
    >
      <Upload className="h-3.5 w-3.5" aria-hidden />
      {importLabel}
    </button>
  ) : null;

  const tertiaryButton = (
    <TertiaryAction
      tpl={tpl}
      canEdit={canEdit}
      activatePending={activatePending}
      archivePending={archivePending}
      onActivate={onActivate}
      onArchive={onArchive}
      activateLabel={activateLabel}
      archiveLabel={archiveLabel}
    />
  );

  const tertiaryMenu =
    hasTertiary && layout === "table" ? (
      <span className="lg:hidden">
        <IconMenu label={moreActionsLabel}>
          <TertiaryAction
            tpl={tpl}
            canEdit={canEdit}
            activatePending={activatePending}
            archivePending={archivePending}
            onActivate={onActivate}
            onArchive={onArchive}
            activateLabel={activateLabel}
            archiveLabel={archiveLabel}
            compact="menu"
          />
        </IconMenu>
      </span>
    ) : null;

  return (
    <div>
      <div className="flex flex-wrap items-center gap-1.5">
        {formControl}
        {importControl}
        {layout === "table" ? (
          <>
            <span className="hidden lg:inline-flex">{tertiaryButton}</span>
            {tertiaryMenu}
          </>
        ) : (
          tertiaryButton
        )}
      </div>
      {uploadFor === tpl.id ? (
        <label className="mt-2 block text-xs text-[var(--muted)]">
          {docxLabel}
          <input
            type="file"
            accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            className="mt-1 block w-full max-w-xs text-sm"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onUpload(tpl.id, file);
            }}
          />
        </label>
      ) : null}
    </div>
  );
}

function Pagination({
  page,
  totalPages,
  onPrev,
  onNext,
  previousLabel,
  nextLabel,
  pageOf,
}: {
  page: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
  previousLabel: string;
  nextLabel: string;
  pageOf: ReactNode;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-between text-sm">
      <Button type="button" variant="secondary" size="sm" disabled={page <= 0} onClick={onPrev}>
        {previousLabel}
      </Button>
      <span className="text-[var(--muted)]">{pageOf}</span>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={onNext}
      >
        {nextLabel}
      </Button>
    </div>
  );
}

export function TemplatesPage() {
  const { t } = useTranslation();
  const { token, canEditTemplates } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [showCreate, setShowCreate] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("demo");
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [uploadFor, setUploadFor] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search.trim(), 300);
  const pageSize = 10;
  const status = parseStatusParam(searchParams.get("status"));

  function setStatus(next: string) {
    const params = new URLSearchParams(searchParams);
    if (next) params.set("status", next);
    else params.delete("status");
    setSearchParams(params, { replace: true });
  }

  useEffect(() => {
    setPage(0);
  }, [status, debouncedSearch]);

  const query = useQuery({
    queryKey: ["templates", page, pageSize, status || "ALL", debouncedSearch || ""],
    queryFn: () =>
      listTemplates(token!, {
        page,
        size: pageSize,
        status: status || undefined,
        q: debouncedSearch || undefined,
      }),
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
  const totalElements = query.data?.totalElements ?? 0;

  const actionProps = {
    canEdit: canEditTemplates,
    uploadFor,
    setUploadFor,
    activatePending: activate.isPending,
    archivePending: archive.isPending,
    onActivate: (id: string) => activate.mutate(id),
    onArchive: (id: string) => archive.mutate(id),
    onUpload: (id: string, file: File) => upload.mutate({ id, file }),
    formLabel: t("templates.form"),
    noVersionLabel: t("templates.noVersion"),
    importLabel: t("templates.import"),
    activateLabel: t("templates.activate"),
    archiveLabel: t("templates.archive"),
    docxLabel: t("templates.docxFile"),
    moreActionsLabel: t("templates.moreActions"),
  };

  return (
    <AppShell
      title={t("templates.title")}
      description={t("templates.description", { placeholders: "{{variables}}" })}
      width="wide"
      actions={
        canEditTemplates ? (
          <Button type="button" onClick={() => setShowCreate((v) => !v)}>
            <FilePlus className="h-4 w-4" aria-hidden />
            {showCreate ? t("templates.hide") : t("templates.new")}
          </Button>
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
        <form onSubmit={onCreate} noValidate className="grid gap-3 md:grid-cols-2">
          <Field label={t("templates.code")} error={fieldErrors.code}>
            <Input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="lettre_simple"
              invalid={!!fieldErrors.code}
            />
          </Field>
          <Field label={t("templates.name")} error={fieldErrors.name}>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              invalid={!!fieldErrors.name}
            />
          </Field>
          <Field label={t("templates.category")}>
            <Input value={category} onChange={(e) => setCategory(e.target.value)} />
          </Field>
          <Field label={t("templates.descriptionLabel")} className="md:col-span-2">
            <Textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <div className="md:col-span-2">
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? t("common.creating") : t("common.create")}
            </Button>
          </div>
        </form>
      </CreatePanel>

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      <Card padding="sm" className="mb-4">
        <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_12rem] md:items-end">
          <label className="block text-sm">
            <span className="sr-only">{t("templates.search")}</span>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted)]"
                aria-hidden
              />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t("templates.searchPlaceholder")}
                className="pl-9"
                aria-label={t("templates.search")}
              />
            </div>
          </label>
          <Field label={t("templates.colStatus")}>
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              {STATUSES.map((s) => (
                <option key={s || "all"} value={s}>
                  {s ? t(`templates.statusLabel.${s}`, { defaultValue: s }) : t("common.all")}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Card>

      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">{t("templates.loadError")}</p> : null}

      <CardList>
        {items.map((tpl) => (
          <CardListItem
            key={tpl.id}
            className={cn(tpl.status === "ARCHIVED" && "opacity-75")}
          >
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="font-medium text-[var(--brand-ink)]">{tpl.name}</p>
                <p className="mt-0.5 font-mono text-xs text-[var(--muted)]">{tpl.code}</p>
              </div>
              <StatusBadge
                status={tpl.status}
                label={t(`templates.statusLabel.${tpl.status}`, { defaultValue: tpl.status })}
              />
            </div>
            <p className="mt-2 text-sm text-[var(--muted)]">
              {tpl.currentVersionNumber != null ? `v${tpl.currentVersionNumber}` : "—"}
            </p>
            <div className="mt-3 border-t border-[var(--line)] pt-2">
              <TemplateActions tpl={tpl} layout="card" {...actionProps} />
            </div>
          </CardListItem>
        ))}
      </CardList>

      <div className="hidden overflow-x-auto rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)] md:block">
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
              <tr
                key={tpl.id}
                className={cn(
                  "border-b border-[var(--line)] align-middle last:border-0",
                  tpl.status === "ARCHIVED" && "opacity-75",
                )}
              >
                <td className="px-4 py-3 font-medium text-[var(--brand-ink)]">{tpl.name}</td>
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
                  <TemplateActions tpl={tpl} layout="table" {...actionProps} />
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

      {query.data && items.length > 0 ? (
        <p className="mt-3 text-sm text-[var(--muted)]">
          {t("templates.countShown", { shown: items.length, total: totalElements })}
        </p>
      ) : null}

      {query.data ? (
        <Pagination
          page={page}
          totalPages={query.data.totalPages}
          onPrev={() => setPage((p) => Math.max(0, p - 1))}
          onNext={() => setPage((p) => p + 1)}
          previousLabel={t("common.previous")}
          nextLabel={t("common.next")}
          pageOf={t("common.pageOf", {
            current: query.data.page + 1,
            total: query.data.totalPages,
            count: query.data.totalElements,
          })}
        />
      ) : null}
    </AppShell>
  );
}
