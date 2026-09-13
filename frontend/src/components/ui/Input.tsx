import type { InputHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

const controlBase =
  "w-full rounded-[var(--radius-lg)] border border-[var(--line)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--ink)] outline-none transition-colors placeholder:text-[var(--muted)] focus:border-[var(--brand)] disabled:cursor-not-allowed disabled:opacity-60";

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
};

export function Input({ className, invalid, ...props }: InputProps) {
  return (
    <input
      className={cn(
        controlBase,
        "min-h-10",
        invalid && "border-[var(--danger)] focus:border-[var(--danger)]",
        className,
      )}
      aria-invalid={invalid || undefined}
      {...props}
    />
  );
}

export { controlBase };
