import { FilePlus, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { Card } from "@/components/ui/Card";
import { cn } from "@/lib/cn";

const actionClass =
  "inline-flex min-h-10 w-full items-center gap-3 rounded-[var(--radius-lg)] border border-[var(--line)] bg-transparent px-3 py-2 text-left text-sm font-medium text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]/40 hover:bg-[var(--brand-soft)]";

export function QuickActionsCard({ className }: { className?: string }) {
  const { t } = useTranslation();

  return (
    <Card padding="md" className={cn(className)}>
      <h2 className="text-[15px] font-semibold tracking-tight text-[var(--brand-ink)]">
        {t("dashboard.shortcuts")}
      </h2>
      <div className="mt-3 flex flex-col gap-2">
        <Link to="/batches" className={actionClass}>
          <Upload className="h-4 w-4 shrink-0 text-[var(--brand)]" aria-hidden />
          {t("dashboard.shortcutBatch")}
        </Link>
        <Link to="/templates" className={actionClass}>
          <FilePlus className="h-4 w-4 shrink-0 text-[var(--brand)]" aria-hidden />
          {t("dashboard.shortcutTemplate")}
        </Link>
      </div>
    </Card>
  );
}
