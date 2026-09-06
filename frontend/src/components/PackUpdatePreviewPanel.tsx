import { useTranslation } from "react-i18next";
import type { PackChangeItem, PackUpdatePreview, PackValidationIssue } from "@/api/businessPacks";

function issueLabel(issue: PackValidationIssue, t: (key: string) => string): string {
  if (issue.message?.startsWith("error.")) {
    const localized = t(issue.message);
    if (localized !== issue.message) return localized;
  }
  return issue.message || issue.code;
}

function changeLabel(
  item: PackChangeItem,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  const code = item.templateOrPromptCode || "";
  const variable = item.variableKey || "";
  const kindKey = `packs.import.update.change.${item.kind}`;
  const localized = t(kindKey, {
    code,
    variable,
    before: item.before || "",
    after: item.after || "",
  });
  if (localized !== kindKey) return localized;
  return `${item.kind}${code ? ` · ${code}` : ""}${variable ? ` · ${variable}` : ""}`;
}

type Props = {
  preview: PackUpdatePreview;
};

/**
 * Update review panel (PRD §122 / Phase 16): old→new version, summary diff, warnings, breaking.
 */
export function PackUpdatePreviewPanel({ preview }: Props) {
  const { t } = useTranslation();
  const summary = preview.summary;
  const breaking = preview.breakingChanges ?? [];
  const changes = preview.changes ?? [];

  return (
    <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 sm:p-6">
      <p className="text-xs font-medium uppercase tracking-wide text-[var(--muted)]">
        {t("packs.import.update.detected")}
      </p>
      <h2 className="mt-1 text-lg text-[var(--brand-ink)]">
        {t("packs.import.update.versionLine", {
          from: preview.installedVersion || "—",
          to: preview.candidateVersion || "—",
        })}
      </h2>
      {preview.updateKind ? (
        <p className="mt-1 text-sm text-[var(--muted)]">
          {t("packs.import.update.kind", { kind: preview.updateKind })}
        </p>
      ) : null}

      <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-[var(--muted)]">{t("packs.import.update.templatesAdded")}</dt>
          <dd className="mt-0.5 tabular-nums">+ {summary.templatesAdded}</dd>
        </div>
        <div>
          <dt className="text-[var(--muted)]">{t("packs.import.update.templatesUpdated")}</dt>
          <dd className="mt-0.5 tabular-nums">~ {summary.templatesUpdated}</dd>
        </div>
        <div>
          <dt className="text-[var(--muted)]">{t("packs.import.update.templatesRemoved")}</dt>
          <dd className="mt-0.5 tabular-nums">− {summary.templatesRemoved}</dd>
        </div>
        <div>
          <dt className="text-[var(--muted)]">{t("packs.import.update.variablesAdded")}</dt>
          <dd className="mt-0.5 tabular-nums">+ {summary.variablesAdded}</dd>
        </div>
        <div>
          <dt className="text-[var(--muted)]">{t("packs.import.update.variablesRemoved")}</dt>
          <dd className="mt-0.5 tabular-nums">− {summary.variablesRemoved}</dd>
        </div>
        <div>
          <dt className="text-[var(--muted)]">{t("packs.import.update.requiredAdded")}</dt>
          <dd className="mt-0.5 tabular-nums">+ {summary.requiredVariablesAdded}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-[var(--muted)]">{t("packs.import.update.promptsChanged")}</dt>
          <dd className="mt-0.5 tabular-nums">{summary.promptsChanged}</dd>
        </div>
      </dl>

      {breaking.length > 0 ? (
        <div className="mt-5">
          <h3 className="text-sm font-semibold text-[var(--danger)]">
            {t("packs.import.update.breakingTitle", { count: breaking.length })}
          </h3>
          {preview.semverBreakingMismatch ? (
            <p className="mt-1 text-sm text-[var(--danger)]">{t("packs.import.update.semverWarning")}</p>
          ) : null}
          <ul className="mt-3 space-y-2 text-sm">
            {breaking.map((issue, idx) => (
              <li
                key={`${issue.code}-b-${idx}`}
                className="rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              >
                <span className="font-medium text-[var(--danger)]">{issue.code}</span>
                <p className="mt-1 text-[var(--brand-ink)]">{issueLabel(issue, t)}</p>
                {issue.templateCode || issue.variable ? (
                  <p className="mt-1 text-xs text-[var(--muted)]">
                    {[issue.templateCode, issue.variable].filter(Boolean).join(" · ")}
                  </p>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {changes.length > 0 ? (
        <div className="mt-5">
          <h3 className="text-sm font-semibold text-[var(--brand-ink)]">
            {t("packs.import.update.diffTitle", { count: changes.length })}
          </h3>
          <ul className="mt-3 max-h-56 space-y-1.5 overflow-y-auto text-sm text-[var(--muted)]">
            {changes.map((item, idx) => (
              <li key={`${item.kind}-${idx}`}>• {changeLabel(item, t)}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="mt-4 text-sm text-[var(--muted)]">{t("packs.import.update.noContentDiff")}</p>
      )}
    </div>
  );
}
