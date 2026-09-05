import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { deleteMyAccount, exportMyData, purgeRetention } from "@/api/settings";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { ConfirmDialog } from "@/components/ConfirmDialog";

type ConfirmKind = "purge" | "delete";

export function PrivacyPage() {
  const { t } = useTranslation();
  const { token, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<ConfirmKind | null>(null);

  const exportMut = useMutation({
    mutationFn: async () => {
      const blob = await exportMyData(token!);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "docuforge-data-export.zip";
      a.click();
      URL.revokeObjectURL(url);
    },
    onSuccess: () => setMessage(t("privacy.exportDone")),
    onError: (err: Error) => setError(err.message),
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteMyAccount(token!),
    onSuccess: () => {
      setConfirm(null);
      logout();
      navigate("/login", { replace: true });
    },
    onError: (err: Error) => {
      setConfirm(null);
      setError(err.message);
    },
  });

  const purgeMut = useMutation({
    mutationFn: () => purgeRetention(token!),
    onSuccess: (res) => {
      setConfirm(null);
      setMessage(t("privacy.purgeDone", { count: res.deleted }));
    },
    onError: (err: Error) => {
      setConfirm(null);
      setError(err.message);
    },
  });

  const pending = purgeMut.isPending || deleteMut.isPending;

  return (
    <AppShell title={t("privacy.title")} description={t("privacy.description")} width="narrow">
      <div className="space-y-6 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
        <section>
          <h2 className="text-base text-[var(--brand-ink)]">{t("privacy.exportTitle")}</h2>
          <p className="mt-1 text-sm text-[var(--muted)]">{t("privacy.exportHint")}</p>
          <button
            type="button"
            className="mt-3 rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
            disabled={exportMut.isPending}
            onClick={() => {
              setError(null);
              setMessage(null);
              exportMut.mutate();
            }}
          >
            {exportMut.isPending ? t("common.loading") : t("privacy.exportAction")}
          </button>
        </section>

        {isAdmin ? (
          <section>
            <h2 className="text-base text-[var(--brand-ink)]">{t("privacy.purgeTitle")}</h2>
            <p className="mt-1 text-sm text-[var(--muted)]">{t("privacy.purgeHint")}</p>
            <button
              type="button"
              className="mt-3 rounded-xl border border-[var(--line)] px-4 py-2 text-sm disabled:opacity-60"
              disabled={purgeMut.isPending}
              onClick={() => {
                setError(null);
                setMessage(null);
                setConfirm("purge");
              }}
            >
              {purgeMut.isPending ? t("common.loading") : t("privacy.purgeAction")}
            </button>
          </section>
        ) : null}

        <section>
          <h2 className="text-base text-[var(--danger)]">{t("privacy.deleteTitle")}</h2>
          <p className="mt-1 text-sm text-[var(--muted)]">{t("privacy.deleteHint")}</p>
          <button
            type="button"
            className="mt-3 rounded-xl bg-[var(--danger)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
            disabled={deleteMut.isPending}
            onClick={() => {
              setError(null);
              setMessage(null);
              setConfirm("delete");
            }}
          >
            {deleteMut.isPending ? t("common.loading") : t("privacy.deleteAction")}
          </button>
        </section>

        {message ? <p className="text-sm text-[var(--brand)]">{message}</p> : null}
        {error ? <p className="text-sm text-[var(--danger)]">{error}</p> : null}
      </div>

      <ConfirmDialog
        open={confirm === "purge"}
        title={t("privacy.purgeTitle")}
        body={t("privacy.purgeConfirm")}
        confirmLabel={pending ? t("common.loading") : t("privacy.purgeAction")}
        cancelLabel={t("common.cancel")}
        pending={pending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => purgeMut.mutate()}
      />

      <ConfirmDialog
        open={confirm === "delete"}
        title={t("privacy.deleteTitle")}
        body={t("privacy.deleteConfirm")}
        confirmLabel={pending ? t("common.loading") : t("privacy.deleteAction")}
        cancelLabel={t("common.cancel")}
        pending={pending}
        danger
        onCancel={() => setConfirm(null)}
        onConfirm={() => deleteMut.mutate()}
      />
    </AppShell>
  );
}
