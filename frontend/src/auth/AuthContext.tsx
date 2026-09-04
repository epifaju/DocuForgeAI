import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { me, type MeResponse } from "@/api/auth";

interface AuthState {
  token: string | null;
  user: MeResponse | null;
  setToken: (token: string | null) => void;
  logout: () => void;
  hasRole: (...roles: string[]) => boolean;
  canEditTemplates: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthState | null>(null);
const STORAGE_KEY = "docuforge.accessToken";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [user, setUser] = useState<MeResponse | null>(null);

  useEffect(() => {
    if (!token) {
      setUser(null);
      return;
    }
    let cancelled = false;
    me(token)
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        if (!cancelled) {
          setUser(null);
          setTokenState(null);
          localStorage.removeItem(STORAGE_KEY);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  const value = useMemo<AuthState>(() => {
    const roles = Array.isArray(user?.roles)
      ? user!.roles
      : user?.roles
        ? Array.from(user.roles as Iterable<string>)
        : [];
    const hasRole = (...wanted: string[]) => wanted.some((r) => roles.includes(r));
    return {
      token,
      user,
      setToken: (next) => {
        setTokenState(next);
        if (next) localStorage.setItem(STORAGE_KEY, next);
        else {
          localStorage.removeItem(STORAGE_KEY);
          setUser(null);
        }
      },
      logout: () => {
        setTokenState(null);
        setUser(null);
        localStorage.removeItem(STORAGE_KEY);
      },
      hasRole,
      canEditTemplates: hasRole("ADMIN", "EDITOR"),
      isAdmin: hasRole("ADMIN"),
    };
  }, [token, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("AuthProvider manquant");
  return ctx;
}
