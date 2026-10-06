import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

export function RequireAuth({ children }) {
  const { token, user, loading } = useAuth();
  const location = useLocation();
  if (loading || (token && !user)) return <p className="muted" style={{ padding: 24 }}>Loading…</p>;
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

export function RequireAdmin({ children }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) return <Navigate to="/" replace />;
  return children;
}

export function GuestOnly({ children }) {
  const { token, user } = useAuth();
  if (token && user) return <Navigate to="/" replace />;
  return children;
}
