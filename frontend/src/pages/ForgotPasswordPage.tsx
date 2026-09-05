import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { forgotPassword } from "@/api/auth";
import { ApiError } from "@/api/client";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { isBlank, isValidEmail } from "@/lib/formValidation";

export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [companyIdentifier, setCompany] = useState("demo");
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (isBlank(companyIdentifier)) next.company = t("validation.required");
    if (isBlank(email)) next.email = t("validation.required");
    else if (!isValidEmail(email)) next.email = t("validation.email");
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;

    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      await forgotPassword(companyIdentifier.trim(), email.trim());
      setMessage(t("auth.forgotSent"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("auth.forgotFailed"));
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
        <h1 className="text-xl text-[var(--brand-ink)]">{t("auth.forgotTitle")}</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">{t("auth.forgotHint")}</p>
        <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
          <label className="block text-sm">
            {t("login.company")}
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2.5"
              value={companyIdentifier}
              onChange={(e) => setCompany(e.target.value)}
            />
            {fieldErrors.company ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.company}</p>
            ) : null}
          </label>
          <label className="block text-sm">
            {t("login.email")}
            <input
              type="email"
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2.5"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            {fieldErrors.email ? (
              <p className="mt-1 text-sm text-[var(--danger)]">{fieldErrors.email}</p>
            ) : null}
          </label>
          {message ? <p className="text-sm text-[var(--brand)]">{message}</p> : null}
          {error ? <p className="text-sm text-[var(--danger)]">{error}</p> : null}
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-xl bg-[var(--brand)] px-4 py-2.5 text-sm font-medium text-white disabled:opacity-60"
          >
            {loading ? t("common.sending") : t("auth.sendResetLink")}
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
