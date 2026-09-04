import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
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
import { AppShell } from "@/components/AppShell";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

export function DocumentDetailPage() {
  const { t, i18n } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const { token } = useAuth();
  const loc = dateLocale(i18n.language);
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
        if (!cancelled) setPdfError(t("document.pdfError"));
      }
    }

    void loadPreview();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [token, doc?.id, doc?.pdfStorageKey, t]);

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
      setEmailFeedback(
        t("document.emailSent", { recipient: result.recipient, status: result.status }),
      );
    },
    onError: (err: Error) => {
      setEmailFeedback(null);
      setEmailError(err.message || t("document.emailFailed"));
    },
  });

  function openConfirm(e: FormEvent) {
    e.preventDefault();
    setEmailError(null);
    setEmailFeedback(null);
    if (!recipient.trim() || !subject.trim()) {
      setEmailError(t("document.emailRequired"));
      return;
    }
    setConfirmOpen(true);
  }

  return (
    <AppShell
      title={doc?.title ?? t("document.title")}
      description={
        doc ? t("document.description", { reference: doc.reference }) : t("document.loadingDesc")
      }
      width="wide"
      actions={
        <Link to="/documents" className="text-sm text-[var(--brand)] underline">
          {t("documents.back")}
        </Link>
      }
    >
      {query.isLoading ? <p>{t("common.loading")}</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">{t("document.notFound")}</p> : null}

      {doc ? (
        <>
          <div className="grid gap-6 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)] xl:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
            {/* Left: metadata + actions */}
            <div className="space-y-5 lg:max-h-[calc(100vh-9rem)] lg:overflow-y-auto lg:pr-1">
              <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
                <p className="font-mono text-xs text-[var(--muted)]">{doc.reference}</p>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <StatusBadge status={doc.status} />
                  <span className="text-sm text-[var(--muted)]">
                    {t("documents.docVersion", { n: doc.documentVersionNumber ?? 1 })}
                  </span>
                </div>
                <dl className="mt-4 grid gap-3 text-sm">
                  <div>
                    <dt className="text-[var(--muted)]">{t("document.template")}</dt>
                    <dd>
                      {doc.templateName}{" "}
                      <span className="text-[var(--muted)]">
                        ({doc.templateCode} · tpl v{doc.templateVersionNumber})
                      </span>
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[var(--muted)]">{t("document.author")}</dt>
                    <dd>{doc.createdByName ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-[var(--muted)]">{t("document.created")}</dt>
                    <dd>{new Date(doc.createdAt).toLocaleString(loc)}</dd>
                  </div>
                </dl>

                <div className="mt-5 flex flex-col gap-2">
                  <Link
                    to={`/documents/${doc.id}/new-version`}
                    className="rounded-xl bg-[var(--brand)] px-4 py-2 text-center text-sm font-medium text-white"
                  >
                    {t("document.newVersion")}
                  </Link>
                  {doc.docxStorageKey ? (
                    <button
                      type="button"
                      className="rounded-xl border border-[var(--brand)] px-4 py-2 text-sm font-medium text-[var(--brand)]"
                      onClick={() => void downloadGeneratedDocx(token!, doc.id, doc.reference)}
                    >
                      {t("document.downloadDocx")}
                    </button>
                  ) : null}
                  {doc.pdfStorageKey ? (
                    <button
                      type="button"
                      className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm font-medium"
                      onClick={() => void downloadGeneratedPdf(token!, doc.id, doc.reference)}
                    >
                      {t("document.downloadPdf")}
                    </button>
                  ) : null}
                </div>
              </section>

              <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
                <h2 className="text-base text-[var(--brand-ink)]">{t("document.emailTitle")}</h2>
                <p className="mt-1 text-xs text-[var(--muted)]">{t("document.emailHint")}</p>

                {formatOptions.length === 0 ? (
                  <p className="mt-3 text-sm text-[var(--muted)]">{t("document.noAttach")}</p>
                ) : (
                  <form className="mt-3 space-y-3" onSubmit={openConfirm} noValidate>
                    <label className="block text-sm">
                      {t("document.recipient")}
                      <input
                        type="email"
                        value={recipient}
                        onChange={(e) => setRecipient(e.target.value)}
                        className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                        placeholder="client@exemple.com"
                      />
                    </label>
                    <label className="block text-sm">
                      {t("document.subject")}
                      <input
                        type="text"
                        value={subject}
                        onChange={(e) => setSubject(e.target.value)}
                        className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                      />
                    </label>
                    <label className="block text-sm">
                      {t("document.message")}
                      <textarea
                        value={message}
                        onChange={(e) => setMessage(e.target.value)}
                        rows={3}
                        className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                      />
                    </label>
                    <label className="block text-sm">
                      {t("document.attachment")}
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
                    {emailFeedback ? (
                      <p className="text-sm text-[var(--brand)]">{emailFeedback}</p>
                    ) : null}
                    <button
                      type="submit"
                      className="w-full rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
                    >
                      {t("document.send")}
                    </button>
                  </form>
                )}
              </section>

              <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5">
                <h2 className="text-base text-[var(--brand-ink)]">{t("document.versions")}</h2>
                <ul className="mt-3 space-y-2 text-sm">
                  {(versions.data ?? []).map((v) => (
                    <li
                      key={v.id}
                      className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--line)] py-2 last:border-0"
                    >
                      <span>
                        v{v.documentVersionNumber} · {v.reference}
                      </span>
                      {v.id === doc.id ? (
                        <span className="text-xs text-[var(--muted)]">{t("document.current")}</span>
                      ) : (
                        <Link to={`/documents/${v.id}`} className="text-[var(--brand)] underline">
                          {t("common.open")}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </section>
            </div>

            {/* Right: sticky PDF preview */}
            <section className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 lg:sticky lg:top-20 lg:self-start">
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 className="text-base text-[var(--brand-ink)]">{t("document.pdfPreview")}</h2>
                {doc.pdfStorageKey && pdfUrl ? (
                  <button
                    type="button"
                    className="text-xs text-[var(--brand)] underline"
                    onClick={() => void downloadGeneratedPdf(token!, doc.id, doc.reference)}
                  >
                    {t("document.download")}
                  </button>
                ) : null}
              </div>
              {!doc.pdfStorageKey ? (
                <div className="flex h-[50vh] items-center justify-center rounded-xl border border-dashed border-[var(--line)] bg-white/60 px-4 text-center text-sm text-[var(--muted)] lg:h-[calc(100vh-12rem)]">
                  {t("document.noPdf", { status: doc.status })}
                </div>
              ) : null}
              {pdfError ? (
                <p className="text-sm text-[var(--danger)]">{pdfError}</p>
              ) : null}
              {doc.pdfStorageKey && !pdfUrl && !pdfError ? (
                <div className="flex h-[50vh] items-center justify-center text-sm text-[var(--muted)] lg:h-[calc(100vh-12rem)]">
                  {t("document.pdfLoading")}
                </div>
              ) : null}
              {pdfUrl ? (
                <iframe
                  title={`${t("document.pdfPreview")} ${doc.reference}`}
                  src={pdfUrl}
                  className="h-[70vh] w-full rounded-xl border border-[var(--line)] bg-white lg:h-[calc(100vh-12rem)]"
                />
              ) : null}
            </section>
          </div>

          {confirmOpen ? (
            <div
              className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4"
              role="dialog"
              aria-modal="true"
              aria-labelledby="email-confirm-title"
            >
              <div className="w-full max-w-md rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 shadow-lg">
                <h3 id="email-confirm-title" className="text-lg text-[var(--brand-ink)]">
                  {t("document.confirmTitle")}
                </h3>
                <p className="mt-2 text-sm text-[var(--muted)]">
                  {t("document.confirmBody", {
                    format: attachmentFormat,
                    reference: doc.reference,
                    recipient,
                  })}
                </p>
                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className="rounded-xl border border-[var(--line)] px-4 py-2 text-sm"
                    disabled={sendMutation.isPending}
                    onClick={() => setConfirmOpen(false)}
                  >
                    {t("common.cancel")}
                  </button>
                  <button
                    type="button"
                    className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                    disabled={sendMutation.isPending}
                    onClick={() => sendMutation.mutate()}
                  >
                    {sendMutation.isPending ? t("document.sending") : t("document.confirmSend")}
                  </button>
                </div>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </AppShell>
  );
}
