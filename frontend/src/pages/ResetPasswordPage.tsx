import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { resetPassword } from "@/api/auth";
import { ApiError } from "@/api/client";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { isBlank } from "@/lib/formValidation";

export function ResetPasswordPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (isBlank(token)) next.token = t("auth.missingToken");
    if (isBlank(password)) next.password = t("validation.required");
    else if (password.length < 8) next.password = t("validation.passwordMin", { min: 8 });
    if (password !== confirm) next.confirm = t("auth.passwordMismatch");
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;

    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      await resetPassword(token, password);
      setMessage(t("auth.resetOk"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("auth.resetFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--surface)] px-6 py-12">
      <div className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-between">
          <p className="brand text-2xl text-[var(--brand-ink)]">DocuForge AI</p>
          <LanguageSwitcher />
        </div>
        <h1 className="text-xl text-[var(--brand-ink)]">{t("auth.resetTitle")}</h1>
        <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
          <label className="block text-sm">
            {t("auth.newPassword")}
            <input
              type="password"
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2.5"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            {fieldErrors.password ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.password}</p>
            ) : null}
          </label>
          <label className="block text-sm">
            {t("auth.confirmPassword")}
            <input
              type="password"
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2.5"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
            {fieldErrors.confirm ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.confirm}</p>
            ) : null}
          </label>
          {fieldErrors.token ? (
            <p className="text-sm text-[var(--danger)]">{fieldErrors.token}</p>
          ) : null}
          {message ? <p className="text-sm text-[var(--brand)]">{message}</p> : null}
          {error ? <p className="text-sm text-[var(--danger)]">{error}</p> : null}
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-xl bg-[var(--brand)] px-4 py-2.5 text-sm font-medium text-white disabled:opacity-60"
          >
            {loading ? t("common.saving") : t("auth.resetSubmit")}
          </button>
        </form>
        <p className="mt-4 text-sm">
          <Link to="/login" className="text-[var(--brand)] underline">
            {t("auth.backToLogin")}
          </Link>
        </p>
      </div>
    </div>
  );
}
