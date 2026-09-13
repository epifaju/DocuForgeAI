import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { MoreHorizontal } from "lucide-react";
import { cn } from "@/lib/cn";

/** Minimal accessible menu — same open/close pattern as AppShell Admin dropdown. */
export function IconMenu({
  label,
  children,
  align = "right",
}: {
  label: string;
  children: ReactNode;
  align?: "left" | "right";
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      if (!wrapRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("mousedown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("mousedown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div className="relative inline-flex" ref={wrapRef}>
      <button
        type="button"
        className={cn(
          "inline-flex min-h-9 min-w-9 items-center justify-center rounded-full border border-[var(--line)] bg-[var(--surface)] text-[var(--muted)] transition-colors",
          "hover:border-[var(--brand)]/40 hover:bg-[var(--brand-soft)] hover:text-[var(--brand-ink)]",
          open && "border-[var(--brand)]/40 bg-[var(--brand-soft)] text-[var(--brand-ink)]",
        )}
        aria-label={label}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-controls={menuId}
        onClick={() => setOpen((v) => !v)}
      >
        <MoreHorizontal className="h-4 w-4" aria-hidden />
      </button>
      {open ? (
        <div
          id={menuId}
          role="menu"
          aria-label={label}
          className={cn(
            "absolute top-full z-40 mt-1 min-w-[10rem] rounded-[var(--radius-lg)] border border-[var(--line)] bg-[var(--surface)] py-1 shadow-lg",
            align === "right" ? "right-0" : "left-0",
          )}
        >
          <div
            onClick={() => setOpen(false)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") setOpen(false);
            }}
          >
            {children}
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function IconMenuItem({
  children,
  onClick,
  disabled,
}: {
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      disabled={disabled}
      className="flex min-h-10 w-full items-center gap-2 px-3 text-left text-sm text-[var(--brand-ink)] transition-colors hover:bg-[var(--bg-accent)] disabled:opacity-40"
      onClick={onClick}
    >
      {children}
    </button>
  );
}
