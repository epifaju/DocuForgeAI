import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Link } from "react-router-dom";
import { getAiStatus } from "@/api/ai";
import { ApiError } from "@/api/client";
import { createDocumentVersion, downloadGeneratedDocx, downloadGeneratedPdf } from "@/api/documents";
import { generateDocument } from "@/api/forms";
import type { FormSchema } from "@/api/types";
import { AiAssistToolbar } from "@/components/AiAssistToolbar";
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
  const [serverMessage, setServerMessage] = useState<string | null>(null);
  const [lastDocId, setLastDocId] = useState<string | null>(null);
  const zodSchema = useMemo(() => schemaToZod(schema), [schema]);
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

  useEffect(() => {
    reset(defaults);
    setServerMessage(null);
    setLastDocId(null);
  }, [schema, defaults, reset]);

  const onSubmit = handleSubmit(async (values) => {
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
        for (const detail of err.details) {
          const formField = toFormKey(detail.field);
          setError(formField as never, { message: detail.message });
        }
        setServerMessage(err.message);
      } else {
        setServerMessage(mode === "new-version" ? "Nouvelle version impossible." : "Generation impossible.");
      }
    }
  });

  return (
    <form onSubmit={onSubmit} className="space-y-5" noValidate>
      <div className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 shadow-sm">
        <p className="text-sm text-[var(--muted)]">Template</p>
        <h2 className="mt-1 text-2xl text-[var(--brand-ink)]">
          {schema.templateName}{" "}
          <span className="text-base font-normal text-[var(--muted)]">v{schema.versionNumber}</span>
        </h2>
        <p className="mt-1 text-sm text-[var(--muted)]">{schema.templateCode}</p>
      </div>

      <div className="space-y-4 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5 shadow-sm">
        {schema.fields.map((field) => {
          const formKey = toFormKey(field.key);
          const error = errors[formKey as keyof typeof errors]?.message as string | undefined;
          const common = "mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 outline-none focus:border-[var(--brand)]";

          return (
            <label key={field.key} className="block">
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
                      value={String((watched as Record<string, unknown>)?.[formKey] ?? "")}
                      aiMode={field.aiMode}
                      onAccept={(text) =>
                        reset({ ...(watched as Record<string, unknown>), [formKey]: text })
                      }
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
        })}
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={isSubmitting}
          className="rounded-xl bg-[var(--brand)] px-5 py-2.5 font-medium text-white transition hover:bg-[var(--brand-ink)] disabled:opacity-60"
        >
          {isSubmitting
            ? mode === "new-version"
              ? "Creation…"
              : "Generation…"
            : submitLabel ?? (mode === "new-version" ? "Creer une nouvelle version" : "Generer le document")}
        </button>
        {serverMessage ? <p className="text-sm text-[var(--muted)]">{serverMessage}</p> : null}
        {lastDocId ? (
          <Link to={`/documents/${lastDocId}`} className="text-sm text-[var(--brand)] underline">
            Voir dans le repository
          </Link>
        ) : null}
      </div>
    </form>
  );
}
