import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(getToken);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(getToken()));

  const logout = useCallback(() => {
    setToken(null);
    setTokenState(null);
    setUser(null);
  }, []);

  // Any 401 on an authenticated call (expired token, deactivated account) signs out.
  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    api.me()
      .then((profile) => !cancelled && setUser(profile))
      .catch(() => !cancelled && logout())
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [token, logout]);

  const acceptAuth = useCallback((auth) => {
    setToken(auth.accessToken);
    setTokenState(auth.accessToken);
    setUser(auth.user);
  }, []);

  const login = useCallback(async (email, password) => acceptAuth(await api.login({ email, password })), [acceptAuth]);
  const register = useCallback(async (body) => acceptAuth(await api.register(body)), [acceptAuth]);

  const value = useMemo(
    () => ({ token, user, setUser, loading, login, register, logout, isAdmin: user?.role === 'ADMIN' }),
    [token, user, loading, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
