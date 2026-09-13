import type { LabelHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

export type FieldProps = {
  label: ReactNode;
  htmlFor?: string;
  hint?: ReactNode;
  error?: ReactNode;
  className?: string;
  labelProps?: LabelHTMLAttributes<HTMLLabelElement>;
  children: ReactNode;
};

export function Field({ label, htmlFor, hint, error, className, labelProps, children }: FieldProps) {
  return (
    <label
      {...labelProps}
      htmlFor={htmlFor ?? labelProps?.htmlFor}
      className={cn("block text-sm text-[var(--ink)]", className)}
    >
      <span className="font-medium">{label}</span>
      <div className="mt-1">{children}</div>
      {error ? <p className="mt-1 text-sm text-[var(--danger)]">{error}</p> : null}
      {!error && hint ? <p className="mt-1 text-xs text-[var(--muted)]">{hint}</p> : null}
    </label>
  );
}
