import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useLocation, useParams } from "react-router-dom";
import {
  disableBusinessPack,
  disablePackTemplate,
  enableBusinessPack,
  enablePackTemplate,
  exportBusinessPack,
  getBusinessPack,
  uninstallBusinessPack,
  type PackTemplate,
} from "@/api/businessPacks";
import { duplicateTemplate } from "@/api/templates";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

type Section = "overview" | "templates" | "versions";

function sectionFromPath(pathname: string): Section {
  if (pathname.endsWith("/templates")) return "templates";
  if (pathname.endsWith("/versions")) return "versions";
  return "overview";
}

function SectionLink({
  to,
  active,
  children,
}: {
  to: string;
  active: boolean;
  children: string;
}) {
  return (
    <Link
      to={to}
      className={[
        "rounded-xl px-3 py-1.5 text-sm transition-colors",
        active
          ? "bg-[var(--bg-accent)] font-medium text-[var(--brand-ink)]"
          : "text-[var(--muted)] hover:text-[var(--brand)]",
      ].join(" ")}
    >
      {children}
    </Link>
  );
}

export function BusinessPackDetailPage() {
  const { t, i18n } = useTranslation();
  const { packId } = useParams<{ packId: string }>();
  const location = useLocation();
  const section = sectionFromPath(location.pathname);
  const { token, isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const loc = dateLocale(i18n.language);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmDisablePack, setConfirmDisablePack] = useState(false);
  const [confirmUninstallPack, setConfirmUninstallPack] = useState(false);
  const [confirmDisableTemplate, setConfirmDisableTemplate] = useState<string | null>(null);
  const [duplicateTarget, setDuplicateTarget] = useState<PackTemplate | null>(null);
  const [duplicateName, setDuplicateName] = useState("");
  const [duplicatedTemplateId, setDuplicatedTemplateId] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["business-pack", packId],
    queryFn: () => getBusinessPack(token!, packId!),
    enabled: !!token && isAdmin && !!packId,
  });

  const pack = query.data;

  function invalidatePack() {
    void queryClient.invalidateQueries({ queryKey: ["business-pack", packId] });
    void queryClient.invalidateQueries({ queryKey: ["business-packs"] });
  }

  const enablePack = useMutation({
    mutationFn: () => enableBusinessPack(token!, packId!),
    onSuccess: () => {
      setMessage(t("packs.enabled"));
      invalidatePack();
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const disablePack = useMutation({
    mutationFn: () => disableBusinessPack(token!, packId!),
    onSuccess: () => {
      setConfirmDisablePack(false);
      setMessage(t("packs.disabled"));
      invalidatePack();
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const uninstallPack = useMutation({
    mutationFn: () => uninstallBusinessPack(token!, packId!),
    onSuccess: (result) => {
      setConfirmUninstallPack(false);
      setMessage(
        t("packs.uninstalled", {
          count: result.historicalDocumentCount,
          archived: result.templatesArchived,
        })
      );
      invalidatePack();
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const exportPack = useMutation({
    mutationFn: () =>
      exportBusinessPack(token!, packId!, `${pack?.slug ?? pack?.packKey ?? "pack"}.zip`),
    onSuccess: () => setMessage(t("packs.exported")),
    onError: (err: Error) => setMessage(err.message),
  });

  const enableTemplate = useMutation({
    mutationFn: (code: string) => enablePackTemplate(token!, packId!, code),
    onSuccess: () => {
      setMessage(t("packs.templateEnabled"));
      invalidatePack();
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const disableTemplate = useMutation({
    mutationFn: (code: string) => disablePackTemplate(token!, packId!, code),
    onSuccess: () => {
      setConfirmDisableTemplate(null);
      setMessage(t("packs.templateDisabled"));
      invalidatePack();
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const duplicate = useMutation({
    mutationFn: () => {
      if (!duplicateTarget?.templateId) throw new Error(t("packs.duplicateMissing"));
      return duplicateTemplate(token!, duplicateTarget.templateId, duplicateName.trim());
    },
    onSuccess: (created) => {
      setDuplicateTarget(null);
      setDuplicatedTemplateId(created.id);
      setMessage(t("packs.duplicateSuccess", { code: created.code }));
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  const base = `/business-packs/${packId}`;

  return (
    <AppShell
      title={pack?.name ?? t("packs.detailTitle")}
      description={pack?.packKey ?? t("packs.detailDescription")}
      width="form"
      actions={
        <div className="flex flex-wrap items-center gap-2">
          {pack ? (
            <button
              type="button"
              className="rounded-xl border border-[var(--line)] px-3.5 py-2 text-sm text-[var(--brand-ink)]"
              disabled={exportPack.isPending}
              onClick={() => exportPack.mutate()}
            >
              {t("packs.export")}
            </button>
          ) : null}
          {pack && pack.status === "DISABLED" ? (
            <button
              type="button"
              className="rounded-xl bg-[var(--brand)] px-3.5 py-2 text-sm font-medium text-white disabled:opacity-60"
              disabled={enablePack.isPending}
              onClick={() => enablePack.mutate()}
            >
              {t("packs.enable")}
            </button>
          ) : null}
          {pack && pack.status !== "DISABLED" && pack.status !== "UNINSTALLED" ? (
            <button
              type="button"
              className="rounded-xl border border-[var(--line)] px-3.5 py-2 text-sm text-[var(--danger)]"
              onClick={() => setConfirmDisablePack(true)}
            >
              {t("packs.disable")}
            </button>
          ) : null}
          {pack && pack.status !== "UNINSTALLED" ? (
            <button
              type="button"
              className="rounded-xl border border-[var(--danger)] px-3.5 py-2 text-sm text-[var(--danger)]"
              onClick={() => setConfirmUninstallPack(true)}
            >
              {t("packs.uninstall")}
            </button>
          ) : null}
          <Link
            to="/business-packs"
            className="rounded-xl border border-[var(--line)] bg-[var(--surface)] px-3.5 py-2 text-sm text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]"
          >
            {t("packs.back")}
          </Link>
        </div>
      }
    >
      <nav className="mb-6 flex flex-wrap gap-2 text-sm">
        <SectionLink to={base} active={section === "overview"}>
          {t("packs.tabOverview")}
        </SectionLink>
        <SectionLink to={`${base}/templates`} active={section === "templates"}>
          {t("packs.tabTemplates")}
        </SectionLink>
        <SectionLink to={`${base}/versions`} active={section === "versions"}>
          {t("packs.tabVersions")}
        </SectionLink>
      </nav>

      {message ? <p className="mb-4 text-sm text-[var(--brand-ink)]">{message}</p> : null}
      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="text-[var(--danger)]">{t("packs.notFound")}</p>
      ) : null}

      {pack && section === "overview" ? (
        <div className="space-y-6">
          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
                  {t("packs.colName")}
                </p>
                <p className="mt-1 text-lg text-[var(--brand-ink)]">{pack.name}</p>
                {pack.description ? (
                  <p className="mt-2 text-sm text-[var(--muted)]">{pack.description}</p>
                ) : null}
              </div>
              <div className="flex flex-wrap gap-2">
                <StatusBadge status={pack.packType} label={t(`packs.type.${pack.packType}`)} />
                <StatusBadge status={pack.status} label={t(`packs.status.${pack.status}`)} />
              </div>
            </div>
            {pack.status === "UPDATE_AVAILABLE" ? (
              <div className="mt-5 rounded-xl border border-[var(--line)] bg-[var(--bg-accent)] px-4 py-3 text-sm">
                <p className="font-medium text-[var(--brand-ink)]">{t("packs.updateAvailableTitle")}</p>
                <p className="mt-1 text-[var(--muted)]">{t("packs.updateAvailableHint")}</p>
                <Link
                  to="/business-packs/import"
                  className="mt-3 inline-flex rounded-xl bg-[var(--brand)] px-3.5 py-1.5 text-sm font-medium text-white"
                >
                  {t("packs.updateAvailableCta")}
                </Link>
              </div>
            ) : null}
            <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-[var(--muted)]">{t("packs.packKey")}</dt>
                <dd className="mt-0.5 break-all text-[var(--brand-ink)]">{pack.packKey}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("packs.colPublisher")}</dt>
                <dd className="mt-0.5 text-[var(--brand-ink)]">
                  {pack.publisherName || "—"}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("packs.colVersion")}</dt>
                <dd className="mt-0.5 tabular-nums text-[var(--brand-ink)]">
                  {pack.currentVersion?.version || "—"}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("packs.installation")}</dt>
                <dd className="mt-0.5 text-[var(--brand-ink)]">
                  {pack.installation
                    ? `${pack.installation.installationType} · ${pack.installation.status}`
                    : "—"}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("packs.createdAt")}</dt>
                <dd className="mt-0.5 tabular-nums text-[var(--brand-ink)]">
                  {new Date(pack.createdAt).toLocaleString(loc)}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">{t("packs.updatedAt")}</dt>
                <dd className="mt-0.5 tabular-nums text-[var(--brand-ink)]">
                  {new Date(pack.updatedAt).toLocaleString(loc)}
                </dd>
              </div>
            </dl>
          </section>

          {pack.prompts.length > 0 ? (
            <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
              <h2 className="text-sm font-semibold text-[var(--brand-ink)]">
                {t("packs.prompts")}
              </h2>
              <ul className="mt-3 space-y-2 text-sm">
                {pack.prompts.map((prompt) => (
                  <li key={prompt.id} className="text-[var(--muted)]">
                    <span className="font-medium text-[var(--brand-ink)]">
                      {prompt.promptCode}
                    </span>{" "}
                    · v{prompt.promptVersion}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      ) : null}

      {pack && section === "templates" ? (
        <section className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
          {pack.templates.length === 0 ? (
            <p className="p-5 text-sm text-[var(--muted)]">{t("packs.templatesEmpty")}</p>
          ) : (
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-[var(--line)] text-xs uppercase tracking-wide text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-3 font-medium">{t("packs.colTemplate")}</th>
                  <th className="px-4 py-3 font-medium">{t("packs.colVersion")}</th>
                  <th className="px-4 py-3 font-medium">{t("packs.colStatus")}</th>
                  <th className="px-4 py-3 font-medium">{t("packs.colActions")}</th>
                </tr>
              </thead>
              <tbody>
                {pack.templates.map((tpl) => (
                  <tr key={tpl.id} className="border-b border-[var(--line)] last:border-0">
                    <td className="px-4 py-3">
                      <div className="font-medium text-[var(--brand-ink)]">{tpl.name}</div>
                      <div className="text-xs text-[var(--muted)]">{tpl.templateCode}</div>
                    </td>
                    <td className="px-4 py-3 tabular-nums">
                      {tpl.templateVersionNumber ?? "—"}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge
                        status={tpl.enabled ? "ACTIVE" : "DISABLED"}
                        label={tpl.enabled ? t("packs.templateOn") : t("packs.templateOff")}
                      />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center gap-3">
                        {tpl.templateId ? (
                          <button
                            type="button"
                            className="text-[var(--brand)] underline-offset-2 hover:underline disabled:opacity-50"
                            disabled={duplicate.isPending}
                            onClick={() => {
                              setDuplicateTarget(tpl);
                              setDuplicateName(
                                t("packs.duplicateDefaultName", { name: tpl.name }),
                              );
                              setMessage(null);
                            }}
                          >
                            {t("packs.duplicate")}
                          </button>
                        ) : null}
                        {pack.status === "DISABLED" ? (
                          <span className="text-xs text-[var(--muted)]">
                            {t("packs.templateNeedsPack")}
                          </span>
                        ) : tpl.enabled ? (
                          <button
                            type="button"
                            className="text-[var(--danger)] underline-offset-2 hover:underline disabled:opacity-50"
                            disabled={disableTemplate.isPending}
                            onClick={() => setConfirmDisableTemplate(tpl.templateCode)}
                          >
                            {t("packs.disable")}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="text-[var(--brand)] underline-offset-2 hover:underline disabled:opacity-50"
                            disabled={enableTemplate.isPending}
                            onClick={() => enableTemplate.mutate(tpl.templateCode)}
                          >
                            {t("packs.enable")}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      ) : null}

      {pack && section === "versions" ? (
        <section className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
          {pack.versions.length === 0 ? (
            <p className="p-5 text-sm text-[var(--muted)]">{t("packs.versionsEmpty")}</p>
          ) : (
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-[var(--line)] text-xs uppercase tracking-wide text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-3 font-medium">{t("packs.colVersion")}</th>
                  <th className="px-4 py-3 font-medium">{t("packs.schemaVersion")}</th>
                  <th className="px-4 py-3 font-medium">{t("packs.colStatus")}</th>
                  <th className="px-4 py-3 font-medium">{t("packs.installedAt")}</th>
                </tr>
              </thead>
              <tbody>
                {pack.versions.map((version) => (
                  <tr key={version.id} className="border-b border-[var(--line)] last:border-0">
                    <td className="px-4 py-3">
                      <span className="font-medium text-[var(--brand-ink)]">
                        {version.version}
                      </span>
                      {version.current ? (
                        <span className="ml-2 text-xs text-[var(--muted)]">
                          ({t("packs.current")})
                        </span>
                      ) : null}
                    </td>
                    <td className="px-4 py-3 text-[var(--muted)]">{version.schemaVersion}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={version.status} />
                    </td>
                    <td className="px-4 py-3 tabular-nums text-[var(--muted)]">
                      {version.installedAt
                        ? new Date(version.installedAt).toLocaleString(loc)
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      ) : null}

      <ConfirmDialog
        open={confirmDisablePack}
        title={t("packs.disableConfirmTitle")}
        body={t("packs.disableConfirmBody", { name: pack?.name ?? "" })}
        confirmLabel={t("packs.disable")}
        cancelLabel={t("common.cancel")}
        pending={disablePack.isPending}
        danger
        onConfirm={() => disablePack.mutate()}
        onCancel={() => setConfirmDisablePack(false)}
      />

      <ConfirmDialog
        open={confirmUninstallPack}
        title={t("packs.uninstallConfirmTitle")}
        body={t("packs.uninstallConfirmBody", { name: pack?.name ?? "" })}
        confirmLabel={t("packs.uninstall")}
        cancelLabel={t("common.cancel")}
        pending={uninstallPack.isPending}
        danger
        onConfirm={() => uninstallPack.mutate()}
        onCancel={() => setConfirmUninstallPack(false)}
      />

      <ConfirmDialog
        open={!!confirmDisableTemplate}
        title={t("packs.disableTemplateConfirmTitle")}
        body={t("packs.disableTemplateConfirmBody", {
          code: confirmDisableTemplate ?? "",
        })}
        confirmLabel={t("packs.disable")}
        cancelLabel={t("common.cancel")}
        pending={disableTemplate.isPending}
        danger
        onConfirm={() => {
          if (confirmDisableTemplate) disableTemplate.mutate(confirmDisableTemplate);
        }}
        onCancel={() => setConfirmDisableTemplate(null)}
      />

      {duplicateTarget ? (
        <div
          className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4"
          role="dialog"
          aria-modal="true"
          onClick={() => {
            if (!duplicate.isPending) setDuplicateTarget(null);
          }}
        >
          <div
            className="w-full max-w-md rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 shadow-lg"
            onClick={(event) => event.stopPropagation()}
          >
            <h3 className="text-lg text-[var(--brand-ink)]">{t("packs.duplicateTitle")}</h3>
            <p className="mt-2 text-sm text-[var(--muted)]">{t("packs.duplicateHint")}</p>
            <label className="mt-4 block text-sm text-[var(--brand-ink)]">
              {t("packs.duplicateName")}
              <input
                className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
                value={duplicateName}
                onChange={(e) => setDuplicateName(e.target.value)}
                disabled={duplicate.isPending}
              />
            </label>
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
                disabled={duplicate.isPending}
                onClick={() => setDuplicateTarget(null)}
              >
                {t("common.cancel")}
              </button>
              <button
                type="button"
                className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                disabled={duplicate.isPending || !duplicateName.trim()}
                onClick={() => duplicate.mutate()}
              >
                {t("packs.duplicateConfirm")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {duplicatedTemplateId ? (
        <p className="mt-4 text-sm">
          <Link
            to="/templates"
            className="text-[var(--brand)] underline-offset-2 hover:underline"
            onClick={() => setDuplicatedTemplateId(null)}
          >
            {t("packs.duplicateOpenTemplates")}
          </Link>
        </p>
      ) : null}
    </AppShell>
  );
}
