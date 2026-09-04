import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "@/api/auth";
import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";

export function LoginPage() {
  const { setToken } = useAuth();
  const navigate = useNavigate();
  const [companyIdentifier, setCompany] = useState("demo");
  const [email, setEmail] = useState("admin@demo.local");
  const [password, setPassword] = useState("changeme_admin_dev_only");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const tokens = await login(companyIdentifier, email, password);
      setToken(tokens.accessToken);
      navigate("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Connexion impossible");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-10">
      <p className="brand text-4xl text-[var(--brand-ink)]">DocuForge AI</p>
      <p className="mt-2 text-[var(--muted)]">Connexion a votre espace documentaire.</p>

      <form onSubmit={onSubmit} className="mt-8 space-y-4 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-6 shadow-sm">
        <label className="block text-sm">
          Societe
          <input
            className="mt-1 w-full rounded-xl border border-[var(--line)] px-3 py-2"
            value={companyIdentifier}
            onChange={(e) => setCompany(e.target.value)}
            required
          />
        </label>
        <label className="block text-sm">
          Email
          <input
            type="email"
            className="mt-1 w-full rounded-xl border border-[var(--line)] px-3 py-2"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label className="block text-sm">
          Mot de passe
          <input
            type="password"
            className="mt-1 w-full rounded-xl border border-[var(--line)] px-3 py-2"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error ? <p className="text-sm text-[var(--danger)]">{error}</p> : null}
        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-xl bg-[var(--brand)] px-4 py-2.5 font-medium text-white hover:bg-[var(--brand-ink)] disabled:opacity-60"
        >
          {loading ? "Connexion…" : "Se connecter"}
        </button>
      </form>
    </div>
  );
}