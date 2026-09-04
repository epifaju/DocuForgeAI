import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  type AttachmentFormat,
  downloadGeneratedDocx,
  downloadGeneratedPdf,
  emailDocument,
  fetchPdfBlob,
  getDocument,
  listDocumentVersions,
} from "@/api/documents";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

export function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { token } = useAuth();
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);

  const [recipient, setRecipient] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [attachmentFormat, setAttachmentFormat] = useState<AttachmentFormat>("DOCX");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [emailFeedback, setEmailFeedback] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["document", id],
    queryFn: () => getDocument(token!, id!),
    enabled: !!token && !!id,
  });

  const versions = useQuery({
    queryKey: ["document-versions", id],
    queryFn: () => listDocumentVersions(token!, id!),
    enabled: !!token && !!id,
  });

  const doc = query.data;

  const formatOptions = useMemo(() => {
    const options: AttachmentFormat[] = [];
    if (doc?.docxStorageKey) options.push("DOCX");
    if (doc?.pdfStorageKey) options.push("PDF");
    if (doc?.docxStorageKey && doc?.pdfStorageKey) options.push("BOTH");
    return options;
  }, [doc?.docxStorageKey, doc?.pdfStorageKey]);

  useEffect(() => {
    if (!doc) return;
    setSubject((prev) => prev || `Document ${doc.reference}`);
    setMessage((prev) => prev || `Veuillez trouver ci-joint le document ${doc.reference}.`);
    if (formatOptions.length > 0 && !formatOptions.includes(attachmentFormat)) {
      setAttachmentFormat(formatOptions[0]);
    }
  }, [doc, formatOptions, attachmentFormat]);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    async function loadPreview() {
      setPdfUrl(null);
      setPdfError(null);
      if (!token || !doc?.pdfStorageKey) return;
      try {
        const blob = await fetchPdfBlob(token, doc.id);
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setPdfUrl(objectUrl);
      } catch {
        if (!cancelled) setPdfError("Apercu PDF indisponible.");
      }
    }

    void loadPreview();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [token, doc?.id, doc?.pdfStorageKey]);

  const sendMutation = useMutation({
    mutationFn: () =>
      emailDocument(token!, doc!.id, {
        recipient: recipient.trim(),
        subject: subject.trim(),
        message: message.trim(),
        attachmentFormat,
        confirmed: true,
      }),
    onSuccess: (result) => {
      setConfirmOpen(false);
      setEmailError(null);
      setEmailFeedback(`Email envoye a ${result.recipient} (${result.status}).`);
    },
    onError: (err: Error) => {
      setEmailFeedback(null);
      setEmailError(err.message || "Envoi impossible.");
    },
  });

  function openConfirm(e: FormEvent) {
    e.preventDefault();
    setEmailError(null);
    setEmailFeedback(null);
    if (!recipient.trim() || !subject.trim()) {
      setEmailError("Destinataire et sujet sont requis.");
      return;
    }
    setConfirmOpen(true);
  }

  return (
    <div className="mx-auto max-w-4xl px-6 py-10">
      <AppHeader subtitle="Detail document — versions, previsualisation et telechargements." />

      <p className="mb-4 text-sm">
        <Link to="/documents" className="text-[var(--brand)] underline">
          ← Retour au repository
        </Link>
      </p>

      {query.isLoading ? <p>Chargement…</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">Document introuvable.</p> : null}

      {doc ? (
        <div className="space-y-6">
          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-mono text-xs text-[var(--muted)]">{doc.reference}</p>
            <h1 className="mt-1 text-2xl text-[var(--brand-ink)]">{doc.title}</h1>
            <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-[var(--muted)]">Statut</dt>
                <dd>{doc.status}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Version document</dt>
                <dd>v{doc.documentVersionNumber ?? 1}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Template</dt>
                <dd>
                  {doc.templateName} ({doc.templateCode}) · tpl v{doc.templateVersionNumber}
                </dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Auteur</dt>
                <dd>{doc.createdByName ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-[var(--muted)]">Cree le</dt>
                <dd>{new Date(doc.createdAt).toLocaleString("fr-FR")}</dd>
              </div>
            </dl>

            <div className="mt-5 flex flex-wrap gap-3">
              <Link
                to={`/documents/${doc.id}/new-version`}
                className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
              >
                Nouvelle version
              </Link>
              {doc.docxStorageKey ? (
                <button
                  type="button"
                  className="rounded-xl border border-[var(--brand)] px-4 py-2 text-sm font-medium text-[var(--brand)]"
                  onClick={() => void downloadGeneratedDocx(token!, doc.id, doc.reference)}
                >
                  Telecharger DOCX
                </button>
              ) : null}
              {doc.pdfStorageKey ? (
                <button
                  type="button"
                  className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm font-medium"
                  onClick={() => void downloadGeneratedPdf(token!, doc.id, doc.reference)}
                >
                  Telecharger PDF
                </button>
              ) : null}
            </div>
          </section>

          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
            <h2 className="text-lg text-[var(--brand-ink)]">Envoyer par email</h2>
            <p className="mt-1 text-sm text-[var(--muted)]">
              Une confirmation est exigee avant l&apos;envoi SMTP (Mailpit en DEV).
            </p>

            {formatOptions.length === 0 ? (
              <p className="mt-3 text-sm text-[var(--muted)]">Aucun fichier attachable pour ce document.</p>
            ) : (
              <form className="mt-4 space-y-3" onSubmit={openConfirm}>
                <label className="block text-sm">
                  <span className="text-[var(--muted)]">Destinataire</span>
                  <input
                    type="email"
                    required
                    value={recipient}
                    onChange={(e) => setRecipient(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                    placeholder="client@exemple.com"
                  />
                </label>
                <label className="block text-sm">
                  <span className="text-[var(--muted)]">Sujet</span>
                  <input
                    type="text"
                    required
                    value={subject}
                    onChange={(e) => setSubject(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  />
                </label>
                <label className="block text-sm">
                  <span className="text-[var(--muted)]">Message</span>
                  <textarea
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    rows={4}
                    className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  />
                </label>
                <label className="block text-sm">
                  <span className="text-[var(--muted)]">Piece jointe</span>
                  <select
                    value={attachmentFormat}
                    onChange={(e) => setAttachmentFormat(e.target.value as AttachmentFormat)}
                    className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  >
                    {formatOptions.map((fmt) => (
                      <option key={fmt} value={fmt}>
                        {fmt}
                      </option>
                    ))}
                  </select>
                </label>
                {emailError ? <p className="text-sm text-[var(--danger)]">{emailError}</p> : null}
                {emailFeedback ? <p className="text-sm text-[var(--brand)]">{emailFeedback}</p> : null}
                <button
                  type="submit"
                  className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
                >
                  Envoyer…
                </button>
              </form>
            )}
          </section>

          {confirmOpen ? (
            <div
              className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4"
              role="dialog"
              aria-modal="true"
              aria-labelledby="email-confirm-title"
            >
              <div className="w-full max-w-md rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 shadow-lg">
                <h3 id="email-confirm-title" className="text-lg text-[var(--brand-ink)]">
                  Confirmer l&apos;envoi
                </h3>
                <p className="mt-2 text-sm text-[var(--muted)]">
                  Envoyer <strong>{attachmentFormat}</strong> de <strong>{doc.reference}</strong> a{" "}
                  <strong>{recipient}</strong> ?
                </p>
                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
                    disabled={sendMutation.isPending}
                    onClick={() => setConfirmOpen(false)}
                  >
                    Annuler
                  </button>
                  <button
                    type="button"
                    className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                    disabled={sendMutation.isPending}
                    onClick={() => sendMutation.mutate()}
                  >
                    {sendMutation.isPending ? "Envoi…" : "Confirmer l'envoi"}
                  </button>
                </div>
              </div>
            </div>
          ) : null}

          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
            <h2 className="text-lg text-[var(--brand-ink)]">Historique des versions</h2>
            <ul className="mt-3 space-y-2 text-sm">
              {(versions.data ?? []).map((v) => (
                <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--line)] py-2 last:border-0">
                  <span>
                    v{v.documentVersionNumber} · {v.reference} · {v.status}
                  </span>
                  {v.id === doc.id ? (
                    <span className="text-[var(--muted)]">Courante</span>
                  ) : (
                    <Link to={`/documents/${v.id}`} className="text-[var(--brand)] underline">
                      Ouvrir
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </section>

          <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
            <h2 className="text-lg text-[var(--brand-ink)]">Apercu PDF</h2>
            {!doc.pdfStorageKey ? (
              <p className="mt-2 text-sm text-[var(--muted)]">
                Pas de PDF pour ce document (statut {doc.status}).
              </p>
            ) : null}
            {pdfError ? <p className="mt-2 text-sm text-[var(--danger)]">{pdfError}</p> : null}
            {pdfUrl ? (
              <iframe
                title={`Apercu ${doc.reference}`}
                src={pdfUrl}
                className="mt-4 h-[70vh] w-full rounded-xl border border-[var(--line)] bg-white"
              />
            ) : null}
          </section>
        </div>
      ) : null}
    </div>
  );
}
