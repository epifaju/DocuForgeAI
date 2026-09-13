import { cva, type VariantProps } from "class-variance-authority";
import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-60",
  {
    variants: {
      variant: {
        primary: "bg-[var(--brand)] text-white hover:bg-[var(--brand-ink)]",
        secondary:
          "border border-[var(--line)] bg-[var(--surface)] text-[var(--brand-ink)] hover:border-[var(--brand)] hover:bg-[var(--bg-accent)]",
        ghost: "text-[var(--muted)] hover:bg-[var(--bg-accent)] hover:text-[var(--brand-ink)]",
        danger: "bg-[var(--danger)] text-white hover:bg-[var(--danger-ink)]",
        dangerOutline:
          "border border-[var(--danger)] bg-[var(--surface)] text-[var(--danger)] hover:bg-[var(--danger-soft)]",
      },
      size: {
        sm: "min-h-9 rounded-[var(--radius-lg)] px-3 py-1.5 text-sm",
        md: "min-h-10 rounded-[var(--radius-lg)] px-4 py-2 text-sm",
        lg: "min-h-11 rounded-[var(--radius-lg)] px-5 py-2.5 text-sm",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  },
);

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants>;

export function Button({ className, variant, size, type = "button", ...props }: ButtonProps) {
  return (
    <button type={type} className={cn(buttonVariants({ variant, size }), className)} {...props} />
  );
}

export { buttonVariants };
