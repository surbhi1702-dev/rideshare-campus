import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, refreshSession, setAccessToken, setSessionExpiredHandler } from '../api/client.js';

const AuthContext = createContext(null);

/**
 * Session state. Nothing is persisted in the browser: on page load the app asks
 * /api/auth/refresh, which works only if the httpOnly refresh cookie is present.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  // A failed silent refresh (session expired, logged out elsewhere, account disabled) signs out.
  useEffect(() => setSessionExpiredHandler(clearSession), [clearSession]);

  useEffect(() => {
    let cancelled = false;
    refreshSession()
      .then((auth) => !cancelled && setUser(auth.user))
      .catch(() => {})
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  const acceptAuth = useCallback((auth) => {
    setAccessToken(auth.accessToken);
    setUser(auth.user);
  }, []);

  const login = useCallback(async (email, password) => acceptAuth(await api.login({ email, password })), [acceptAuth]);
  const register = useCallback(async (body) => acceptAuth(await api.register(body)), [acceptAuth]);
  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* already logged out server-side; clear locally anyway */
    }
    clearSession();
  }, [clearSession]);

  const value = useMemo(
    () => ({ user, setUser, loading, login, register, logout, isAdmin: user?.role === 'ADMIN' }),
    [user, loading, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
