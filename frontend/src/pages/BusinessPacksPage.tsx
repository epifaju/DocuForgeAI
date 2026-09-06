import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import {
  disableBusinessPack,
  enableBusinessPack,
  listBusinessPacks,
  type PackSummary,
} from "@/api/businessPacks";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

const STATUS_FILTERS = ["", "INSTALLED", "DISABLED", "UPDATE_AVAILABLE", "UNINSTALLED"] as const;
const TYPE_FILTERS = ["", "OFFICIAL", "CUSTOM", "THIRD_PARTY"] as const;

function parseEnumParam(raw: string | null, allowed: readonly string[]): string {
  if (!raw) return "";
  return allowed.includes(raw) ? raw : "";
}

export function BusinessPacksPage() {
  const { t, i18n } = useTranslation();
  const { token, isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState(searchParams.get("search") ?? "");
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmDisable, setConfirmDisable] = useState<PackSummary | null>(null);
  const loc = dateLocale(i18n.language);

  const status = parseEnumParam(searchParams.get("status"), STATUS_FILTERS);
  const type = parseEnumParam(searchParams.get("type"), TYPE_FILTERS);

  function setFilter(key: "status" | "type", value: string) {
    const params = new URLSearchParams(searchParams);
    if (value) params.set(key, value);
    else params.delete(key);
    setSearchParams(params, { replace: true });
  }

  useEffect(() => {
    setPage(0);
  }, [status, type]);

  const filters = useMemo(
    () => ({
      status: status || undefined,
      type: type || undefined,
      search: search.trim() || undefined,
      page,
      size: 20,
      sort: "updatedAt,desc",
    }),
    [status, type, search, page],
  );

  const query = useQuery({
    queryKey: ["business-packs", filters],
    queryFn: () => listBusinessPacks(token!, filters),
    enabled: !!token && isAdmin,
  });

  const enable = useMutation({
    mutationFn: (packId: string) => enableBusinessPack(token!, packId),
    onSuccess: () => {
      setMessage(t("packs.enabled"));
      void queryClient.invalidateQueries({ queryKey: ["business-packs"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const disable = useMutation({
    mutationFn: (packId: string) => disableBusinessPack(token!, packId),
    onSuccess: () => {
      setConfirmDisable(null);
      setMessage(t("packs.disabled"));
      void queryClient.invalidateQueries({ queryKey: ["business-packs"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  const items = query.data?.items ?? [];
  const totalPages = query.data?.totalPages ?? 0;

  return (
    <AppShell
      title={t("packs.title")}
      description={t("packs.description")}
      width="wide"
      actions={
        <Link
          to="/business-packs/import"
          className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
        >
          {t("packs.import.cta")}
        </Link>
      }
    >
      <form
        className="mb-6 grid gap-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 sm:grid-cols-4"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          const params = new URLSearchParams(searchParams);
          if (search.trim()) params.set("search", search.trim());
          else params.delete("search");
          setSearchParams(params, { replace: true });
          void query.refetch();
        }}
      >
        <label className="block text-sm sm:col-span-2">
          {t("packs.search")}
          <input
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder={t("packs.searchPlaceholder")}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          />
        </label>
        <label className="block text-sm">
          {t("packs.statusFilter")}
          <select
            value={status}
            onChange={(e) => {
              setFilter("status", e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            {STATUS_FILTERS.map((s) => (
              <option key={s || "all"} value={s}>
                {s ? t(`packs.status.${s}`) : t("common.all")}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          {t("packs.typeFilter")}
          <select
            value={type}
            onChange={(e) => {
              setFilter("type", e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            {TYPE_FILTERS.map((tp) => (
              <option key={tp || "all"} value={tp}>
                {tp ? t(`packs.type.${tp}`) : t("common.all")}
              </option>
            ))}
          </select>
        </label>
      </form>

      {message ? <p className="mb-4 text-sm text-[var(--brand-ink)]">{message}</p> : null}
      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="text-[var(--danger)]">{t("packs.loadError")}</p>
      ) : null}
      {!query.isLoading && !query.isError && items.length === 0 ? (
        <p className="text-[var(--muted)]">{t("packs.empty")}</p>
      ) : null}

      {items.length > 0 ? (
        <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-[var(--line)] text-xs uppercase tracking-wide text-[var(--muted)]">
              <tr>
                <th className="px-4 py-3 font-medium">{t("packs.colName")}</th>
                <th className="px-4 py-3 font-medium">{t("packs.colPublisher")}</th>
                <th className="px-4 py-3 font-medium">{t("packs.colVersion")}</th>
                <th className="px-4 py-3 font-medium">{t("packs.colType")}</th>
                <th className="px-4 py-3 font-medium">{t("packs.colStatus")}</th>
                <th className="px-4 py-3 font-medium">{t("packs.colUpdated")}</th>
                <th className="px-4 py-3 font-medium">{t("packs.colActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((pack) => (
                <tr key={pack.id} className="border-b border-[var(--line)] last:border-0">
                  <td className="px-4 py-3">
                    <div className="font-medium text-[var(--brand-ink)]">{pack.name}</div>
                    <div className="text-xs text-[var(--muted)]">{pack.packKey}</div>
                  </td>
                  <td className="px-4 py-3 text-[var(--muted)]">
                    {pack.publisherName || "—"}
                  </td>
                  <td className="px-4 py-3 tabular-nums">{pack.currentVersion || "—"}</td>
                  <td className="px-4 py-3">
                    <StatusBadge
                      status={pack.packType}
                      label={t(`packs.type.${pack.packType}`)}
                    />
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge
                      status={pack.status}
                      label={t(`packs.status.${pack.status}`)}
                    />
                  </td>
                  <td className="px-4 py-3 tabular-nums text-[var(--muted)]">
                    {new Date(pack.updatedAt).toLocaleString(loc)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-2">
                      <Link
                        to={`/business-packs/${pack.id}`}
                        className="text-[var(--brand)] underline-offset-2 hover:underline"
                      >
                        {t("packs.view")}
                      </Link>
                      {pack.status === "DISABLED" ? (
                        <button
                          type="button"
                          className="text-[var(--brand)] underline-offset-2 hover:underline disabled:opacity-50"
                          disabled={enable.isPending}
                          onClick={() => enable.mutate(pack.id)}
                        >
                          {t("packs.enable")}
                        </button>
                      ) : pack.status !== "UNINSTALLED" ? (
                        <button
                          type="button"
                          className="text-[var(--danger)] underline-offset-2 hover:underline disabled:opacity-50"
                          disabled={disable.isPending}
                          onClick={() => setConfirmDisable(pack)}
                        >
                          {t("packs.disable")}
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between gap-3 text-sm">
          <p className="text-[var(--muted)]">
            {t("common.pageOf", {
              current: page + 1,
              total: totalPages,
              count: query.data?.totalElements ?? 0,
            })}
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-50"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              {t("common.previous")}
            </button>
            <button
              type="button"
              className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-50"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              {t("common.next")}
            </button>
          </div>
        </div>
      ) : null}

      <ConfirmDialog
        open={!!confirmDisable}
        title={t("packs.disableConfirmTitle")}
        body={t("packs.disableConfirmBody", { name: confirmDisable?.name ?? "" })}
        confirmLabel={t("packs.disable")}
        cancelLabel={t("common.cancel")}
        pending={disable.isPending}
        danger
        onConfirm={() => {
          if (confirmDisable) disable.mutate(confirmDisable.id);
        }}
        onCancel={() => setConfirmDisable(null)}
      />
    </AppShell>
  );
}
