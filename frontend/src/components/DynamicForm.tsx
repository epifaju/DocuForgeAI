import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Link } from "react-router-dom";
import { getAiStatus } from "@/api/ai";
import { ApiError } from "@/api/client";
import { createDocumentVersion, downloadGeneratedDocx, downloadGeneratedPdf } from "@/api/documents";
import { generateDocument } from "@/api/forms";
import type { FormFieldSchema, FormSchema } from "@/api/types";
import { AiAssistToolbar } from "@/components/AiAssistToolbar";
import { groupFieldsByPrefix, isFullWidthField } from "@/lib/formLayout";
import { defaultValues, schemaToZod, toApiData, toFormData, toFormKey } from "@/lib/schemaToZod";
import { useAuth } from "@/auth/AuthContext";

interface Props {
  schema: FormSchema;
  initialValues?: Record<string, unknown>;
  mode?: "generate" | "new-version";
  sourceDocumentId?: string;
  submitLabel?: string;
}

export function DynamicForm({
  schema,
  initialValues,
  mode = "generate",
  sourceDocumentId,
  submitLabel,
}: Props) {
  const { token } = useAuth();
  const formRef = useRef<HTMLFormElement>(null);
  const [serverMessage, setServerMessage] = useState<string | null>(null);
  const [lastDocId, setLastDocId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const zodSchema = useMemo(() => schemaToZod(schema), [schema]);
  const sections = useMemo(() => groupFieldsByPrefix(schema.fields), [schema.fields]);
  const defaults = useMemo(
    () => ({
      ...defaultValues(schema),
      ...toFormData(initialValues ?? {}),
    }),
    [schema, initialValues],
  );

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(zodSchema),
    defaultValues: defaults,
  });

  const watched = useWatch({ control });
  const aiStatus = useQuery({
    queryKey: ["ai-status"],
    queryFn: () => getAiStatus(token!),
    enabled: !!token,
    staleTime: 60_000,
  });
  const aiOnline = aiStatus.data?.enabled === true;

  const errorCount = useMemo(() => Object.keys(errors).length, [errors]);

  useEffect(() => {
    reset(defaults);
    setServerMessage(null);
    setLastDocId(null);
    setCollapsed({});
  }, [schema, defaults, reset]);

  function scrollToFirstError(errorKeys?: string[]) {
    const keys = errorKeys ?? Object.keys(errors);
    if (!keys.length || !formRef.current) return;
    const firstKey = keys[0]!;
    const el = formRef.current.querySelector<HTMLElement>(`[data-field-key="${CSS.escape(firstKey)}"]`);
    if (!el) return;
    const sectionId = el.getAttribute("data-section-id");
    if (sectionId) {
      setCollapsed((prev) => ({ ...prev, [sectionId]: false }));
    }
    requestAnimationFrame(() => {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      const focusable = el.querySelector<HTMLElement>("input, textarea, select");
      focusable?.focus({ preventScroll: true });
    });
  }

  const onSubmit = handleSubmit(
    async (values) => {
      if (!token) return;
      setServerMessage(null);
      setLastDocId(null);
      try {
        const data = toApiData(values as Record<string, unknown>);
        const doc =
          mode === "new-version" && sourceDocumentId
            ? await createDocumentVersion(token, sourceDocumentId, data, schema.templateName)
            : await generateDocument(
                token,
                schema.templateId,
                schema.templateVersionId,
                data,
                schema.templateName,
              );
        setLastDocId(doc.id);
        setServerMessage(
          mode === "new-version"
            ? `Version ${doc.documentVersionNumber} creee (${doc.reference}, ${doc.status}).`
            : `Document ${doc.reference} genere (${doc.status}).`,
        );
        if (doc.status === "COMPLETED") {
          await downloadGeneratedPdf(token, doc.id, doc.reference);
        } else {
          await downloadGeneratedDocx(token, doc.id, doc.reference);
        }
      } catch (err) {
        if (err instanceof ApiError) {
          const keys: string[] = [];
          for (const detail of err.details) {
            const formField = toFormKey(detail.field);
            keys.push(formField);
            setError(formField as never, { message: detail.message });
          }
          setServerMessage(err.message);
          scrollToFirstError(keys);
        } else {
          setServerMessage(mode === "new-version" ? "Nouvelle version impossible." : "Generation impossible.");
        }
      }
    },
    (invalid) => {
      scrollToFirstError(Object.keys(invalid));
    },
  );

  const defaultLabel =
    submitLabel ?? (mode === "new-version" ? "Creer une nouvelle version" : "Generer le document");

  return (
    <form ref={formRef} onSubmit={onSubmit} className="space-y-5 pb-28" noValidate>
      <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 shadow-sm">
        <p className="text-sm text-[var(--muted)]">Template</p>
        <h2 className="mt-1 text-2xl text-[var(--brand-ink)]">
          {schema.templateName}{" "}
          <span className="text-base font-normal text-[var(--muted)]">v{schema.versionNumber}</span>
        </h2>
        <p className="mt-1 text-sm text-[var(--muted)]">{schema.templateCode}</p>
        {sections.length > 1 ? (
          <nav className="mt-4 flex flex-wrap gap-2" aria-label="Sections du formulaire">
            {sections.map((section) => (
              <a
                key={section.id}
                href={`#section-${section.id}`}
                className="rounded-lg border border-[var(--line)] bg-white px-2.5 py-1 text-xs text-[var(--brand)] hover:border-[var(--brand)]"
                onClick={() => setCollapsed((prev) => ({ ...prev, [section.id]: false }))}
              >
                {section.title}
                <span className="ml-1 text-[var(--muted)]">({section.fields.length})</span>
              </a>
            ))}
          </nav>
        ) : null}
      </div>

      {sections.map((section) => {
        const isCollapsed = collapsed[section.id] === true;
        const sectionErrorCount = section.fields.filter(
          (f) => errors[toFormKey(f.key) as keyof typeof errors],
        ).length;

        return (
          <section
            key={section.id}
            id={`section-${section.id}`}
            className="scroll-mt-24 rounded-2xl border border-[var(--line)] bg-[var(--surface)] shadow-sm"
          >
            <button
              type="button"
              className="flex w-full items-center justify-between gap-3 px-5 py-3 text-left"
              onClick={() =>
                setCollapsed((prev) => ({ ...prev, [section.id]: !isCollapsed }))
              }
              aria-expanded={!isCollapsed}
            >
              <span className="font-medium text-[var(--brand-ink)]">
                {section.title}
                <span className="ml-2 text-sm font-normal text-[var(--muted)]">
                  {section.fields.length} champ{section.fields.length > 1 ? "s" : ""}
                </span>
                {sectionErrorCount > 0 ? (
                  <span className="ml-2 text-sm font-normal text-[var(--danger)]">
                    · {sectionErrorCount} erreur{sectionErrorCount > 1 ? "s" : ""}
                  </span>
                ) : null}
              </span>
              <span className="text-sm text-[var(--muted)]">{isCollapsed ? "Afficher" : "Masquer"}</span>
            </button>

            {!isCollapsed ? (
              <div className="grid gap-x-4 gap-y-3 border-t border-[var(--line)] px-5 py-4 sm:grid-cols-2">
                {section.fields.map((field) => (
                  <FieldControl
                    key={field.key}
                    field={field}
                    sectionId={section.id}
                    register={register}
                    control={control}
                    errors={errors}
                    watched={watched as Record<string, unknown>}
                    aiOnline={aiOnline}
                    onAiAccept={(text) => {
                      const formKey = toFormKey(field.key);
                      reset({ ...(watched as Record<string, unknown>), [formKey]: text });
                    }}
                  />
                ))}
              </div>
            ) : null}
          </section>
        );
      })}

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-[var(--line)] bg-[var(--surface)]/95 backdrop-blur-sm">
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-3 px-6 py-3">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-xl bg-[var(--brand)] px-5 py-2.5 font-medium text-white transition hover:bg-[var(--brand-ink)] disabled:opacity-60"
          >
            {isSubmitting
              ? mode === "new-version"
                ? "Creation…"
                : "Generation…"
              : defaultLabel}
          </button>
          {errorCount > 0 ? (
            <button
              type="button"
              className="text-sm text-[var(--danger)] underline"
              onClick={() => scrollToFirstError()}
            >
              {errorCount} erreur{errorCount > 1 ? "s" : ""} — aller a la premiere
            </button>
          ) : null}
          {serverMessage ? <p className="text-sm text-[var(--muted)]">{serverMessage}</p> : null}
          {lastDocId ? (
            <Link to={`/documents/${lastDocId}`} className="text-sm text-[var(--brand)] underline">
              Voir dans le repository
            </Link>
          ) : null}
        </div>
      </div>
    </form>
  );
}

function FieldControl({
  field,
  sectionId,
  register,
  control,
  errors,
  watched,
  aiOnline,
  onAiAccept,
}: {
  field: FormFieldSchema;
  sectionId: string;
  register: ReturnType<typeof useForm>["register"];
  control: ReturnType<typeof useForm>["control"];
  errors: ReturnType<typeof useForm>["formState"]["errors"];
  watched: Record<string, unknown>;
  aiOnline: boolean;
  onAiAccept: (text: string) => void;
}) {
  const formKey = toFormKey(field.key);
  const error = errors[formKey as keyof typeof errors]?.message as string | undefined;
  const common =
    "mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 outline-none focus:border-[var(--brand)]";
  const spanClass = isFullWidthField(field) ? "sm:col-span-2" : "";

  return (
    <label
      className={`block ${spanClass}`}
      data-field-key={formKey}
      data-section-id={sectionId}
    >
      <span className="text-sm font-medium">
        {field.label}
        {field.required ? <span className="text-[var(--danger)]"> *</span> : null}
      </span>

      {field.component === "textarea" ? (
        <>
          <textarea
            className={`${common} min-h-28`}
            placeholder={field.placeholder ?? undefined}
            {...register(formKey)}
          />
          {aiOnline && field.aiEnabled ? (
            <AiAssistToolbar
              value={String(watched?.[formKey] ?? "")}
              aiMode={field.aiMode}
              onAccept={onAiAccept}
            />
          ) : null}
        </>
      ) : field.component === "checkbox" ? (
        <div className="mt-2">
          <input type="checkbox" className="size-4 accent-[var(--brand)]" {...register(formKey)} />
        </div>
      ) : field.component === "select" ? (
        <select className={common} {...register(formKey)}>
          <option value="">—</option>
          {(field.validation.options ?? []).map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      ) : field.component === "multiselect" ? (
        <Controller
          name={formKey}
          control={control}
          render={({ field: rhf }) => (
            <select
              multiple
              className={`${common} min-h-28`}
              value={(rhf.value as string[]) ?? []}
              onChange={(e) =>
                rhf.onChange(Array.from(e.target.selectedOptions).map((o) => o.value))
              }
            >
              {(field.validation.options ?? []).map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          )}
        />
      ) : (
        <input
          className={common}
          type={
            field.component === "number"
              ? "number"
              : field.component === "date"
                ? "date"
                : field.component === "datetime"
                  ? "datetime-local"
                  : field.component === "email"
                    ? "email"
                    : field.component === "tel"
                      ? "tel"
                      : "text"
          }
          step={field.type === "CURRENCY" || field.type === "DECIMAL" ? "0.01" : undefined}
          placeholder={field.placeholder ?? undefined}
          {...register(formKey)}
        />
      )}

      {error ? <p className="mt-1 text-sm text-[var(--danger)]">{String(error)}</p> : null}
    </label>
  );
}
