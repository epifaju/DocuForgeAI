import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { createUser, listUsers, updateUser } from "@/api/users";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

const ROLE_OPTIONS = ["ADMIN", "EDITOR", "USER", "VIEWER"] as const;

export function UsersPage() {
  const { token, isAdmin, user: me } = useAuth();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [roles, setRoles] = useState<string[]>(["USER"]);
  const [message, setMessage] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["admin-users"],
    queryFn: () => listUsers(token!),
    enabled: !!token && isAdmin,
  });

  const create = useMutation({
    mutationFn: () =>
      createUser(token!, {
        email: email.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        roles,
        enabled: true,
      }),
    onSuccess: (u) => {
      setMessage(`Utilisateur ${u.email} cree.`);
      setEmail("");
      setPassword("");
      setFirstName("");
      setLastName("");
      setRoles(["USER"]);
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  const toggle = useMutation({
    mutationFn: (row: {
      id: string;
      firstName: string;
      lastName: string;
      roles: string[];
      enabled: boolean;
    }) =>
      updateUser(token!, row.id, {
        firstName: row.firstName,
        lastName: row.lastName,
        roles: row.roles,
        enabled: !row.enabled,
      }),
    onSuccess: () => {
      setMessage("Utilisateur mis a jour.");
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: Error) => setMessage(err.message),
  });

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  function onCreate(e: FormEvent) {
    e.preventDefault();
    setMessage(null);
    create.mutate();
  }

  function toggleRole(role: string) {
    setRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <AppHeader subtitle="Gestion des utilisateurs de la societe (ADMIN)." />

      <form
        onSubmit={onCreate}
        className="mb-8 space-y-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-5"
      >
        <p className="text-sm font-medium text-[var(--brand-ink)]">Nouvel utilisateur</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            Prenom
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
            />
          </label>
          <label className="block text-sm">
            Nom
            <input
              className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
            />
          </label>
        </div>
        <label className="block text-sm">
          Email
          <input
            type="email"
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label className="block text-sm">
          Mot de passe
          <input
            type="password"
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            required
          />
        </label>
        <fieldset className="text-sm">
          <legend className="mb-2">Roles</legend>
          <div className="flex flex-wrap gap-3">
            {ROLE_OPTIONS.map((role) => (
              <label key={role} className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={roles.includes(role)}
                  onChange={() => toggleRole(role)}
                />
                {role}
              </label>
            ))}
          </div>
        </fieldset>
        <button
          type="submit"
          disabled={create.isPending || roles.length === 0}
          className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
        >
          {create.isPending ? "Creation…" : "Creer l'utilisateur"}
        </button>
      </form>

      {message ? <p className="mb-4 text-sm text-[var(--muted)]">{message}</p> : null}

      {query.isLoading ? <p>Chargement…</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">Impossible de charger les utilisateurs.</p> : null}

      <ul className="space-y-3">
        {(query.data?.items ?? []).map((u) => (
          <li
            key={u.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4"
          >
            <div>
              <p className="font-medium">
                {u.firstName} {u.lastName}
              </p>
              <p className="text-sm text-[var(--muted)]">
                {u.email} · {(u.roles ?? []).join(", ")} · {u.enabled ? "actif" : "desactive"}
              </p>
            </div>
            <button
              type="button"
              disabled={toggle.isPending || u.id === me?.id}
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm disabled:opacity-40"
              onClick={() =>
                toggle.mutate({
                  id: u.id,
                  firstName: u.firstName,
                  lastName: u.lastName,
                  roles: u.roles,
                  enabled: u.enabled,
                })
              }
            >
              {u.enabled ? "Desactiver" : "Activer"}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
