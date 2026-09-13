import type { SelectHTMLAttributes } from "react";
import { cn } from "@/lib/cn";
import { controlBase } from "@/components/ui/Input";

export type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  invalid?: boolean;
};

export function Select({ className, invalid, children, ...props }: SelectProps) {
  return (
    <select
      className={cn(
        controlBase,
        "min-h-10",
        invalid && "border-[var(--danger)] focus:border-[var(--danger)]",
        className,
      )}
      aria-invalid={invalid || undefined}
      {...props}
    >
      {children}
    </select>
  );
}
