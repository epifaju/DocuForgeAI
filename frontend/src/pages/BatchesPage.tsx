import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { createBatch, listBatches } from "@/api/batches";
import { listTemplates } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

export function BatchesPage() {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const [templateId, setTemplateId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const batches = useQuery({
    queryKey: ["batches"],
    queryFn: () => listBatches(token!),
    enabled: !!token,
    refetchInterval: 3000,
  });

  const templates = useQuery({
    queryKey: ["templates"],
    queryFn: () => listTemplates(token!),
    enabled: !!token,
  });

  const upload = useMutation({
    mutationFn: async () => {
      if (!token || !file || !templateId) throw new Error("Fichier et template requis");
      return createBatch(token, file, templateId);
    },
    onSuccess: (job) => {
      setMessage(`Batch ${job.id} accepte (${job.status}).`);
      setFile(null);
      void queryClient.invalidateQueries({ queryKey: ["batches"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  return (
    <div className="mx-auto max-w-4xl px-6 py-10">
      <AppHeader subtitle="Generation batch CSV — traitement asynchrone, ZIP et rapport d'erreurs." />

      <form
        className="mb-8 space-y-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5"
        onSubmit={(e) => {
          e.preventDefault();
          upload.mutate();
        }}
      >
        <label className="block text-sm">
          Template
          <select
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
            value={templateId}
            onChange={(e) => setTemplateId(e.target.value)}
            required
          >
            <option value="">Choisir…</option>
            {(templates.data?.items ?? [])
              .filter((t) => t.status === "ACTIVE" && t.currentVersionId)
              .map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.code})
                </option>
              ))}
          </select>
        </label>
        <label className="block text-sm">
          Fichier CSV
          <input
            type="file"
            accept=".csv,text/csv"
            className="mt-1 block w-full text-sm"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            required
          />
        </label>
        <p className="text-xs text-[var(--muted)]">
          Les en-tetes CSV doivent correspondre aux cles de variables (ex.{" "}
          <code>client.firstName</code>), ou fournir un mapping via API.
        </p>
        <button
          type="submit"
          disabled={upload.isPending}
          className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
        >
          {upload.isPending ? "Envoi…" : "Lancer le batch"}
        </button>
        {message ? <p className="text-sm text-[var(--muted)]">{message}</p> : null}
      </form>

      <ul className="space-y-3">
        {(batches.data?.items ?? []).map((job) => (
          <li key={job.id} className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="font-mono text-xs text-[var(--muted)]">{job.id}</p>
                <p className="mt-1 text-sm">
                  {job.status} · {job.successfulItems}/{job.totalItems} ok · {job.failedItems} erreurs
                </p>
              </div>
              <Link to={`/batches/${job.id}`} className="text-sm text-[var(--brand)] underline">
                Detail
              </Link>
            </div>
          </li>
        ))}
      </ul>
      {!batches.isLoading && (batches.data?.items?.length ?? 0) === 0 ? (
        <p className="mt-4 text-[var(--muted)]">Aucun batch pour le moment.</p>
      ) : null}
    </div>
  );
}
