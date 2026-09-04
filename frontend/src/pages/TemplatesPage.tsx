import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { listTemplates } from "@/api/forms";
import {
  activateTemplate,
  archiveTemplate,
  createTemplate,
  uploadTemplateVersion,
} from "@/api/templates";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

export function TemplatesPage() {
  const { token, canEditTemplates } = useAuth();
  const queryClient = useQueryClient();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("demo");
  const [message, setMessage] = useState<string | null>(null);
  const [uploadFor, setUploadFor] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["templates"],
    queryFn: () => listTemplates(token!),
    enabled: !!token,
  });

  const create = useMutation({
    mutationFn: () =>
      createTemplate(token!, {
        code: code.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
        category: category.trim() || undefined,
      }),
    onSuccess: (tpl) => {
      setMessage(`Template ${tpl.code} cree (DRAFT). Uploadez un DOCX puis activez.`);
      setCode("");
      setName("");
      setDescription("");
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const upload = useMutation({
    mutationFn: async ({ id, file }: { id: string; file: File }) => {
      await uploadTemplateVersion(token!, id, file, true);
      return id;
    },
    onSuccess: () => {
      setMessage("Version DOCX uploadee.");
      setUploadFor(null);
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const activate = useMutation({
    mutationFn: (id: string) => activateTemplate(token!, id),
    onSuccess: (tpl) => {
      setMessage(`Template ${tpl.code} ACTIVE.`);
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const archive = useMutation({
    mutationFn: (id: string) => archiveTemplate(token!, id),
    onSuccess: (tpl) => {
      setMessage(`Template ${tpl.code} archive.`);
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  function onCreate(e: FormEvent) {
    e.preventDefault();
    setMessage(null);
    create.mutate();
  }

  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <AppHeader subtitle="Creez un template, uploadez un DOCX {{variables}}, puis activez-le." />

      {canEditTemplates ? (
        <form
          onSubmit={onCreate}
          className="mb-8 space-y-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5"
        >
          <p className="text-sm font-medium text-[var(--brand-ink)]">Nouveau template</p>
          <label className="block text-sm">
            Code
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="lettre_simple"
              pattern="^[a-zA-Z][a-zA-Z0-9_.-]{0,99}$"
              required
            />
          </label>
          <label className="block text-sm">
            Nom
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </label>
          <label className="block text-sm">
            Categorie
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            />
          </label>
          <label className="block text-sm">
            Description
            <textarea
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          <button
            type="submit"
            disabled={create.isPending}
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {create.isPending ? "Creation…" : "Creer le template"}
          </button>
        </form>
      ) : (
        <p className="mb-6 text-sm text-[var(--muted)]">
          Lecture seule — seuls ADMIN / EDITOR peuvent creer ou uploader des templates.
        </p>
      )}

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      {query.isLoading ? <p>Chargement…</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">Impossible de charger les templates.</p> : null}

      <ul className="space-y-3">
        {(query.data?.items ?? []).map((tpl) => (
          <li key={tpl.id} className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="font-medium">{tpl.name}</p>
                <p className="text-sm text-[var(--muted)]">
                  {tpl.code} · {tpl.status}
                  {tpl.currentVersionNumber != null ? ` · v${tpl.currentVersionNumber}` : ""}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {tpl.currentVersionId ? (
                  <Link
                    to={`/forms/${tpl.currentVersionId}`}
                    className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
                  >
                    Formulaire
                  </Link>
                ) : (
                  <span className="self-center text-sm text-[var(--muted)]">Aucune version</span>
                )}
                {canEditTemplates ? (
                  <>
                    <button
                      type="button"
                      className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
                      onClick={() => setUploadFor(uploadFor === tpl.id ? null : tpl.id)}
                    >
                      Upload DOCX
                    </button>
                    {tpl.status !== "ACTIVE" && tpl.currentVersionId ? (
                      <button
                        type="button"
                        className="rounded-xl border border-[var(--brand)] px-3 py-2 text-sm text-[var(--brand)]"
                        disabled={activate.isPending}
                        onClick={() => activate.mutate(tpl.id)}
                      >
                        Activer
                      </button>
                    ) : null}
                    {tpl.status === "ACTIVE" ? (
                      <button
                        type="button"
                        className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm text-[var(--muted)]"
                        disabled={archive.isPending}
                        onClick={() => archive.mutate(tpl.id)}
                      >
                        Archiver
                      </button>
                    ) : null}
                  </>
                ) : null}
              </div>
            </div>
            {uploadFor === tpl.id ? (
              <label className="mt-3 block text-sm">
                Fichier DOCX (variables {"{{...}}"})
                <input
                  type="file"
                  accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  className="mt-1 block w-full text-sm"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) upload.mutate({ id: tpl.id, file });
                  }}
                />
              </label>
            ) : null}
          </li>
        ))}
      </ul>

      {!query.isLoading && (query.data?.items?.length ?? 0) === 0 ? (
        <p className="mt-6 text-[var(--muted)]">
          Aucun template.{" "}
          {canEditTemplates
            ? "Creez-en un ci-dessus, ou importez templates/demo/lettre-simple.docx."
            : "Demandez a un ADMIN/EDITOR d'en creer."}
        </p>
      ) : null}
    </div>
  );
}
