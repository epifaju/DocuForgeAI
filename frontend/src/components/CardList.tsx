import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { Card } from "@/components/ui/Card";

/** Mobile card stack — pair with a `hidden md:block` table for desktop. */
export function CardList({
  children,
  className,
  empty,
}: {
  children: ReactNode;
  className?: string;
  empty?: ReactNode;
}) {
  return (
    <div className={cn("grid gap-3 md:hidden", className)} role="list">
      {children}
      {empty}
    </div>
  );
}

export function CardListItem({
  children,
  className,
  onClick,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
}) {
  return (
    <Card
      role="listitem"
      padding="sm"
      className={cn(onClick && "cursor-pointer transition-colors hover:bg-[var(--bg-accent)]/40", className)}
      onClick={onClick}
    >
      {children}
    </Card>
  );
}
