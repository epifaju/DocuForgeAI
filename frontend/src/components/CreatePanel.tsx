import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

/** Collapsible creation panel — list stays primary; open via AppShell actions. */
export function CreatePanel({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  if (!open) return null;
  return (
    <div className="mb-6 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="text-sm font-medium text-[var(--brand-ink)]">{title}</p>
        <button
          type="button"
          onClick={onClose}
          className="text-sm text-[var(--muted)] underline-offset-2 hover:underline"
        >
          {t("common.close")}
        </button>
      </div>
      {children}
    </div>
  );
}
