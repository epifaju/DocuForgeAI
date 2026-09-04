import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { getSettings, updateSettings } from "@/api/settings";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

type Section = "company" | "ai" | "email" | "users";

function sectionFromPath(pathname: string): Section {
  if (pathname.endsWith("/ai")) return "ai";
  if (pathname.endsWith("/email")) return "email";
  if (pathname.endsWith("/users")) return "users";
  return "company";
}

export function SettingsPage() {
  const { token, isAdmin } = useAuth();
  const location = useLocation();
  const section = sectionFromPath(location.pathname);
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [companyAi, setCompanyAi] = useState(true);
  const [fromAddress, setFromAddress] = useState("");
  const [message, setMessage] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["admin-settings"],
    queryFn: () => getSettings(token!),
    enabled: !!token && isAdmin,
  });

  useEffect(() => {
    if (!query.data) return;
    setName(query.data.company.name);
    setCompanyAi(query.data.ai.companyEnabled);
    setFromAddress(query.data.email.fromAddress ?? "");
  }, [query.data]);

  const save = useMutation({
    mutationFn: () =>
      updateSettings(token!, {
        company: { name: name.trim() },
        ai: { companyEnabled: companyAi },
        email: { fromAddress: fromAddress.trim() },
      }),
    onSuccess: () => {
      setMessage("Parametres enregistres.");
      void queryClient.invalidateQueries({ queryKey: ["admin-settings"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  if (section === "users") {
    return <Navigate to="/users" replace />;
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setMessage(null);
    save.mutate();
  }

  const data = query.data;

  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <AppHeader subtitle="Parametres de la societe (ADMIN)." />

      <nav className="mb-6 flex flex-wrap gap-3 text-sm">
        <SectionLink to="/settings/company" active={section === "company"}>
          Societe
        </SectionLink>
        <SectionLink to="/settings/ai" active={section === "ai"}>
          IA
        </SectionLink>
        <SectionLink to="/settings/email" active={section === "email"}>
          Email
        </SectionLink>
        <SectionLink to="/users" active={false}>
          Utilisateurs
        </SectionLink>
      </nav>

      {query.isLoading ? <p className="text-[var(--muted)]">Chargement…</p> : null}
      {query.isError ? (
        <p className="text-[var(--danger)]">{(query.error as Error).message}</p>
      ) : null}

      {data ? (
        <form
          onSubmit={onSubmit}
          className="space-y-6 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5"
        >
          {section === "company" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">Societe</p>
              <label className="block text-sm">
                Nom affiche
                <input
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  maxLength={200}
                />
              </label>
              <label className="block text-sm">
                Identifiant (connexion)
                <input
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-[var(--bg-accent)] px-3 py-2 text-[var(--muted)]"
                  value={data.company.identifier}
                  readOnly
                />
              </label>
              <p className="text-xs text-[var(--muted)]">
                L&apos;identifiant sert au login et ne peut pas etre modifie.
              </p>
            </div>
          ) : null}

          {section === "ai" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">Assistance IA</p>
              <p className="text-sm text-[var(--muted)]">
                Plateforme : {data.ai.platformEnabled ? "active" : "desactivee"} ·{" "}
                {data.ai.provider} / {data.ai.model}
              </p>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={companyAi}
                  onChange={(e) => setCompanyAi(e.target.checked)}
                />
                Autoriser l&apos;IA pour cette societe
              </label>
              <p className="text-xs text-[var(--muted)]">
                Effectif :{" "}
                {data.ai.platformEnabled && companyAi
                  ? "disponible"
                  : "indisponible (plateforme ou societe)"}
                . La generation de documents ne depend pas de l&apos;IA.
              </p>
            </div>
          ) : null}

          {section === "email" ? (
            <div className="space-y-3">
              <p className="text-sm font-medium text-[var(--brand-ink)]">Email</p>
              <p className="text-sm text-[var(--muted)]">
                SMTP plateforme : {data.email.platformEnabled ? "actif" : "desactive"} · defaut{" "}
                {data.email.platformFrom || "—"}
              </p>
              <label className="block text-sm">
                Expediteur (override societe)
                <input
                  type="email"
                  className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
                  value={fromAddress}
                  onChange={(e) => setFromAddress(e.target.value)}
                  placeholder={data.email.platformFrom || "noreply@example.com"}
                />
              </label>
              <p className="text-xs text-[var(--muted)]">
                Laisser vide pour reprendre l&apos;expediteur plateforme. Confirmation
                utilisateur obligatoire avant envoi.
              </p>
            </div>
          ) : null}

          {message ? <p className="text-sm text-[var(--brand)]">{message}</p> : null}

          <button
            type="submit"
            disabled={save.isPending}
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm text-white disabled:opacity-60"
          >
            {save.isPending ? "Enregistrement…" : "Enregistrer"}
          </button>
        </form>
      ) : null}
    </div>
  );
}

function SectionLink({
  to,
  active,
  children,
}: {
  to: string;
  active: boolean;
  children: string;
}) {
  return (
    <Link
      to={to}
      className={
        active
          ? "font-medium text-[var(--brand-ink)] underline"
          : "text-[var(--brand)] underline-offset-2 hover:underline"
      }
    >
      {children}
    </Link>
  );
}
