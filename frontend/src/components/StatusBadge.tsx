const TONE: Record<string, string> = {
  ACTIVE: "bg-[var(--bg-accent)] text-[var(--brand-ink)]",
  COMPLETED: "bg-[var(--bg-accent)] text-[var(--brand-ink)]",
  SUCCESS: "bg-[var(--bg-accent)] text-[var(--brand-ink)]",
  DRAFT: "bg-white text-[var(--muted)] ring-1 ring-[var(--line)]",
  GENERATED: "bg-white text-[var(--muted)] ring-1 ring-[var(--line)]",
  PENDING: "bg-white text-[var(--muted)] ring-1 ring-[var(--line)]",
  QUEUED: "bg-white text-[var(--muted)] ring-1 ring-[var(--line)]",
  RUNNING: "bg-[#eef3e8] text-[var(--brand-ink)]",
  CONVERTING: "bg-[#eef3e8] text-[var(--brand-ink)]",
  PROCESSING: "bg-[#eef3e8] text-[var(--brand-ink)]",
  ARCHIVED: "bg-[#eeeae2] text-[var(--muted)]",
  DISABLED: "bg-[#eeeae2] text-[var(--muted)]",
  FAILED: "bg-[#f5e6e6] text-[var(--danger)]",
  FAILURE: "bg-[#f5e6e6] text-[var(--danger)]",
  PARTIALLY_FAILED: "bg-[#f5e6e6] text-[var(--danger)]",
};

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const tone = TONE[status] ?? "bg-white text-[var(--muted)] ring-1 ring-[var(--line)]";
  return (
    <span
      className={`inline-block rounded-md px-2 py-0.5 text-xs font-medium tracking-wide ${tone}`}
    >
      {label ?? status}
    </span>
  );
}
