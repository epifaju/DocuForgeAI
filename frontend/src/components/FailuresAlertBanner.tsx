import { AlertTriangle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { buttonVariants } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

export function FailuresAlertBanner({
  count,
  className,
}: {
  count: number;
  className?: string;
}) {
  const { t } = useTranslation();
  if (count <= 0) return null;

  return (
    <div
      role="status"
      className={cn(
        "flex flex-wrap items-center gap-3 rounded-[var(--radius-xl)] border border-[var(--danger)]/35 bg-[var(--danger-soft)] px-4 py-3 sm:px-5",
        className,
      )}
    >
      <span
        className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-[var(--danger)]/15 text-[var(--danger)]"
        aria-hidden
      >
        <AlertTriangle className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-[var(--danger)]">
          {t("dashboard.failuresBannerTitle", { count })}
        </p>
        <p className="mt-0.5 text-sm text-[var(--danger-ink)]/80">
          {t("dashboard.failuresBannerHint")}
        </p>
      </div>
      <Link
        to="/documents?status=FAILED"
        className={cn(buttonVariants({ variant: "dangerOutline", size: "sm" }), "min-h-10 shrink-0")}
      >
        {t("dashboard.failuresBannerAction")}
      </Link>
    </div>
  );
}
