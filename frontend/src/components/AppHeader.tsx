import { Link } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";

export function AppHeader({ subtitle }: { subtitle: string }) {
  const { logout, isAdmin } = useAuth();
  return (
    <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="brand text-3xl text-[var(--brand-ink)]">DocuForge AI</p>
        <p className="mt-1 text-[var(--muted)]">{subtitle}</p>
        <nav className="mt-3 flex flex-wrap gap-4 text-sm">
          <Link to="/dashboard" className="text-[var(--brand)] underline-offset-2 hover:underline">
            Dashboard
          </Link>
          <Link to="/templates" className="text-[var(--brand)] underline-offset-2 hover:underline">
            Templates
          </Link>
          <Link to="/documents" className="text-[var(--brand)] underline-offset-2 hover:underline">
            Documents
          </Link>
          <Link to="/batches" className="text-[var(--brand)] underline-offset-2 hover:underline">
            Batches
          </Link>
          <Link to="/audit" className="text-[var(--brand)] underline-offset-2 hover:underline">
            Audit
          </Link>
          {isAdmin ? (
            <>
              <Link to="/settings" className="text-[var(--brand)] underline-offset-2 hover:underline">
                Settings
              </Link>
              <Link to="/users" className="text-[var(--brand)] underline-offset-2 hover:underline">
                Users
              </Link>
            </>
          ) : null}
        </nav>
      </div>
      <button onClick={logout} className="text-sm text-[var(--muted)] underline">
        Deconnexion
      </button>
    </header>
  );
}
