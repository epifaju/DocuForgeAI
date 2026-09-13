import { cva, type VariantProps } from "class-variance-authority";
import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";

const badgeVariants = cva(
  "inline-block rounded-[var(--radius-sm)] px-2 py-0.5 text-xs font-medium tracking-wide",
  {
    variants: {
      tone: {
        success: "bg-[var(--brand-soft)] text-[var(--brand-ink)]",
        neutral: "bg-[var(--surface)] text-[var(--muted)] ring-1 ring-[var(--line)]",
        progress: "bg-[var(--brand-soft)] text-[var(--brand-ink)]",
        muted: "bg-[var(--neutral-soft)] text-[var(--muted)]",
        danger: "bg-[var(--danger-soft)] text-[var(--danger)]",
        warning: "bg-[var(--warning-soft)] text-[var(--warning)]",
        info: "bg-[var(--info-soft)] text-[var(--info)]",
      },
    },
    defaultVariants: {
      tone: "neutral",
    },
  },
);

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>;

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}

export { badgeVariants };
