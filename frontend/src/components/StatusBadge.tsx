import { Badge, type BadgeProps } from "@/components/ui/Badge";

type Tone = NonNullable<BadgeProps["tone"]>;

const TONE: Record<string, Tone> = {
  ACTIVE: "success",
  COMPLETED: "success",
  SUCCESS: "success",
  INSTALLED: "success",
  VALID: "success",
  OFFICIAL: "success",
  DRAFT: "neutral",
  GENERATED: "neutral",
  PENDING: "neutral",
  QUEUED: "neutral",
  UPLOADED: "neutral",
  CUSTOM: "neutral",
  THIRD_PARTY: "neutral",
  RUNNING: "progress",
  CONVERTING: "progress",
  PROCESSING: "progress",
  VALIDATING: "progress",
  INSTALLING: "progress",
  UPDATE_AVAILABLE: "progress",
  ARCHIVED: "muted",
  DISABLED: "muted",
  UNINSTALLED: "muted",
  SUPERSEDED: "muted",
  EXPIRED: "muted",
  FAILED: "danger",
  FAILURE: "danger",
  PARTIALLY_FAILED: "danger",
  INVALID: "danger",
  BROKEN: "danger",
};

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const tone = TONE[status] ?? "neutral";
  return <Badge tone={tone}>{label ?? status}</Badge>;
}
