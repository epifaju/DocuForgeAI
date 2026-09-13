import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/api/client";
import { aiFormalize, aiGenerate, aiRewrite, aiSummarize } from "@/api/ai";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/Button";

interface Props {
  value: string;
  onAccept: (text: string) => void;
  aiMode?: string | null;
}

export function AiAssistToolbar({ value, onAccept, aiMode }: Props) {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [suggestion, setSuggestion] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(op: "rewrite" | "formalize" | "summarize" | "generate") {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      const result =
        op === "rewrite"
          ? await aiRewrite(token, value)
          : op === "formalize"
            ? await aiFormalize(token, value)
            : op === "summarize"
              ? await aiSummarize(token, value)
              : await aiGenerate(
                  token,
                  aiMode === "GENERATE_PARAGRAPH"
                    ? t("ai.promptGenerateParagraph")
                    : t("ai.promptGenerateComplete"),
                  undefined,
                  value,
                );
      setSuggestion(result.result);
    } catch (err) {
      setSuggestion(null);
      setError(err instanceof ApiError ? err.message : t("ai.unavailable"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-2 space-y-2">
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={busy || !value.trim()}
          onClick={() => void run("rewrite")}
        >
          {t("ai.rewrite")}
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={busy || !value.trim()}
          onClick={() => void run("formalize")}
        >
          {t("ai.formalize")}
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={busy || !value.trim()}
          onClick={() => void run("summarize")}
        >
          {t("ai.summarize")}
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={busy}
          onClick={() => void run("generate")}
          className="border-[var(--brand)] text-[var(--brand)]"
        >
          {t("ai.generate")}
        </Button>
      </div>
      {busy ? <p className="text-xs text-[var(--muted)]">{t("ai.busy")}</p> : null}
      {error ? <p className="text-xs text-[var(--danger)]">{error}</p> : null}
      {suggestion ? (
        <div className="rounded-[var(--radius-lg)] border border-[var(--line)] bg-[var(--surface)] p-3 text-sm">
          <p className="whitespace-pre-wrap">{suggestion}</p>
          <div className="mt-2 flex gap-2">
            <Button
              type="button"
              size="sm"
              onClick={() => {
                onAccept(suggestion);
                setSuggestion(null);
              }}
            >
              {t("ai.accept")}
            </Button>
            <Button type="button" variant="secondary" size="sm" onClick={() => setSuggestion(null)}>
              {t("ai.reject")}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
