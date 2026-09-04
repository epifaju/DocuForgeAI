import { useState } from "react";
import { ApiError } from "@/api/client";
import { aiFormalize, aiGenerate, aiRewrite, aiSummarize } from "@/api/ai";
import { useAuth } from "@/auth/AuthContext";

interface Props {
  value: string;
  onAccept: (text: string) => void;
  aiMode?: string | null;
}

export function AiAssistToolbar({ value, onAccept, aiMode }: Props) {
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
                    ? "Redige un paragraphe professionnel adapte au champ du formulaire."
                    : "Complete ce champ de facon professionnelle.",
                  undefined,
                  value,
                );
      setSuggestion(result.result);
    } catch (err) {
      setSuggestion(null);
      setError(err instanceof ApiError ? err.message : "IA indisponible.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-2 space-y-2">
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={busy || !value.trim()}
          onClick={() => void run("rewrite")}
          className="rounded-lg border border-[var(--line)] px-2 py-1 text-xs disabled:opacity-40"
        >
          Reecrire
        </button>
        <button
          type="button"
          disabled={busy || !value.trim()}
          onClick={() => void run("formalize")}
          className="rounded-lg border border-[var(--line)] px-2 py-1 text-xs disabled:opacity-40"
        >
          Formaliser
        </button>
        <button
          type="button"
          disabled={busy || !value.trim()}
          onClick={() => void run("summarize")}
          className="rounded-lg border border-[var(--line)] px-2 py-1 text-xs disabled:opacity-40"
        >
          Resumer
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => void run("generate")}
          className="rounded-lg border border-[var(--brand)] px-2 py-1 text-xs text-[var(--brand)] disabled:opacity-40"
        >
          Generer
        </button>
      </div>
      {busy ? <p className="text-xs text-[var(--muted)]">IA en cours…</p> : null}
      {error ? <p className="text-xs text-[var(--danger)]">{error}</p> : null}
      {suggestion ? (
        <div className="rounded-xl border border-[var(--line)] bg-white p-3 text-sm">
          <p className="whitespace-pre-wrap">{suggestion}</p>
          <div className="mt-2 flex gap-2">
            <button
              type="button"
              className="rounded-lg bg-[var(--brand)] px-3 py-1 text-xs text-white"
              onClick={() => {
                onAccept(suggestion);
                setSuggestion(null);
              }}
            >
              Accepter
            </button>
            <button
              type="button"
              className="rounded-lg border border-[var(--line)] px-3 py-1 text-xs"
              onClick={() => setSuggestion(null)}
            >
              Rejeter
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
