import type { TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/cn";
import { controlBase } from "@/components/ui/Input";

export type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  invalid?: boolean;
};

export function Textarea({ className, invalid, ...props }: TextareaProps) {
  return (
    <textarea
      className={cn(
        controlBase,
        "min-h-24 resize-y",
        invalid && "border-[var(--danger)] focus:border-[var(--danger)]",
        className,
      )}
      aria-invalid={invalid || undefined}
      {...props}
    />
  );
}
