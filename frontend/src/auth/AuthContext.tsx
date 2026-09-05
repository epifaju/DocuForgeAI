import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { me, type MeResponse, type TokenResponse } from "@/api/auth";
import {
  ACCESS_SKEW_MS,
  bootstrapSession,
  clearSession,
  getAccessExpiresAt,
  getAccessToken,
  refreshSession,
  revokeSessionRemote,
  setSession as persistSession,
  subscribeSession,
} from "@/auth/session";

interface AuthState {
  token: string | null;
  ready: boolean;
  user: MeResponse | null;
  setSession: (tokens: TokenResponse) => void;
  /** @deprecated Prefer setSession — kept for compatibility. */
  setToken: (token: string | null) => void;
  logout: () => void;
  hasRole: (...roles: string[]) => boolean;
  canEditTemplates: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getAccessToken());
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<MeResponse | null>(null);

  useEffect(() => subscribeSession(() => setTokenState(getAccessToken())), []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const session = await bootstrapSession();
      if (cancelled) return;
      setTokenState(session?.accessToken ?? getAccessToken());
      setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!ready) return;
    if (!token) {
      setUser(null);
      return;
    }
    let cancelled = false;
    me(token)
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(async () => {
        if (cancelled) return;
        const session = await refreshSession();
        if (session) {
          try {
            setUser(await me(session.accessToken));
            return;
          } catch {
            /* fall through */
          }
        }
        setUser(null);
        clearSession();
        setTokenState(null);
      });
    return () => {
      cancelled = true;
    };
  }, [token, ready]);

  useEffect(() => {
    if (!token) return;

    let timer: ReturnType<typeof setTimeout> | undefined;
    let cancelled = false;

    const schedule = () => {
      const expiresAt = getAccessExpiresAt();
      if (expiresAt == null) return;
      const delay = Math.max(5_000, expiresAt - Date.now() - ACCESS_SKEW_MS);
      timer = setTimeout(() => {
        void refreshSession().then((session) => {
          if (cancelled) return;
          if (session) schedule();
          else setTokenState(null);
        });
      }, delay);
    };

    schedule();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [token]);

  const applySession = useCallback((tokens: TokenResponse) => {
    persistSession(tokens);
    setTokenState(tokens.accessToken);
  }, []);

  const logout = useCallback(() => {
    void (async () => {
      await revokeSessionRemote();
      clearSession();
      setUser(null);
      setTokenState(null);
    })();
  }, []);

  const value = useMemo<AuthState>(() => {
    const roles = Array.isArray(user?.roles)
      ? user!.roles
      : user?.roles
        ? Array.from(user.roles as Iterable<string>)
        : [];
    const hasRole = (...wanted: string[]) => wanted.some((r) => roles.includes(r));
    return {
      token,
      ready,
      user,
      setSession: applySession,
      setToken: (next) => {
        if (next) {
          setTokenState(next);
        } else {
          clearSession();
          setUser(null);
          setTokenState(null);
        }
      },
      logout,
      hasRole,
      canEditTemplates: hasRole("ADMIN", "EDITOR"),
      isAdmin: hasRole("ADMIN"),
    };
  }, [token, ready, user, applySession, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("AuthProvider manquant");
  return ctx;
}
