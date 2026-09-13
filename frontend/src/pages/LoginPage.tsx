import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, Link } from "react-router-dom";
import { login } from "@/api/auth";
import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { isBlank, isValidEmail } from "@/lib/formValidation";

export function LoginPage() {
  const { setSession } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [companyIdentifier, setCompany] = useState("demo");
  const [email, setEmail] = useState("admin@demo.local");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (isBlank(companyIdentifier)) next.company = t("validation.required");
    if (isBlank(email)) next.email = t("validation.required");
    else if (!isValidEmail(email)) next.email = t("validation.email");
    if (isBlank(password)) next.password = t("validation.required");
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;

    setLoading(true);
    setError(null);
    try {
      const tokens = await login(companyIdentifier, email, password);
      setSession(tokens);
      navigate("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("login.failed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative flex flex-col justify-between overflow-hidden px-8 py-10 lg:px-12 lg:py-14">
        <div
          className="pointer-events-none absolute inset-0 -z-10"
          aria-hidden
          style={{
            background:
              "radial-gradient(900px 480px at 15% 10%, var(--wash-brand) 0%, transparent 55%), radial-gradient(700px 400px at 90% 80%, var(--wash-warm) 0%, transparent 50%), linear-gradient(160deg, var(--bg-accent) 0%, var(--bg) 55%, var(--wash-warm) 100%)",
          }}
        />
        <div className="flex items-start justify-between gap-4">
          <p className="brand text-4xl text-[var(--brand-ink)] sm:text-5xl lg:text-6xl">
            DocuForge AI
          </p>
          <LanguageSwitcher className="relative z-10" />
        </div>
        <div className="mt-10 max-w-md lg:mt-0">
          <h1 className="brand text-2xl text-[var(--brand-ink)] sm:text-3xl">{t("login.tagline")}</h1>
          <p className="mt-3 text-[var(--muted)]">{t("login.subtitle")}</p>
        </div>
        <p className="mt-10 hidden text-sm text-[var(--muted)] lg:block">{t("login.footer")}</p>
      </aside>

      <main className="flex items-center justify-center bg-[var(--surface)] px-6 py-12 lg:px-10">
        <div className="w-full max-w-md">
          <h2 className="text-xl font-semibold text-[var(--brand-ink)]">{t("login.title")}</h2>
          <p className="mt-1 text-sm text-[var(--muted)]">{t("login.hint")}</p>

          <form onSubmit={onSubmit} noValidate className="mt-8 space-y-4">
            <Field label={t("login.company")} error={fieldErrors.company}>
              <Input
                value={companyIdentifier}
                onChange={(e) => setCompany(e.target.value)}
                autoComplete="organization"
                invalid={!!fieldErrors.company}
              />
            </Field>
            <Field label={t("login.email")} error={fieldErrors.email}>
              <Input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                invalid={!!fieldErrors.email}
              />
            </Field>
            <Field label={t("login.password")} error={fieldErrors.password}>
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder={t("login.passwordPlaceholder")}
                invalid={!!fieldErrors.password}
              />
            </Field>
            {error ? <p className="text-sm text-[var(--danger)]">{error}</p> : null}
            <Button type="submit" disabled={loading} className="w-full" size="lg">
              {loading ? t("login.submitting") : t("login.submit")}
            </Button>
            <p className="text-center text-sm">
              <Link to="/forgot-password" className="text-[var(--brand)] underline">
                {t("auth.forgotLink")}
              </Link>
            </p>
          </form>
        </div>
      </main>
    </div>
  );
}
