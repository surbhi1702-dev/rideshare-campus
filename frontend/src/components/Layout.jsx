import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { useRealtime } from '../realtime/RealtimeContext.jsx';

export default function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const { unread, toast, dismissToast } = useRealtime();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            <img src="/favicon.svg" alt="" width="26" height="26" />
            RideShare Campus
          </Link>
          <nav className="nav" aria-label="Main">
            <NavLink to="/" end>Home</NavLink>
            <NavLink to="/rides/new">Offer a ride</NavLink>
            <NavLink to="/search">Find a ride</NavLink>
            <NavLink to="/my-rides">My rides</NavLink>
            <NavLink to="/notifications">
              Notifications
              {unread > 0 && <span className="badge" aria-label={`${unread} unread`}>{unread}</span>}
            </NavLink>
            {isAdmin && <NavLink to="/admin">Admin</NavLink>}
          </nav>
          <div className="topbar-user">
            <NavLink to="/profile" style={{ color: '#fff' }}>{user?.name}</NavLink>
            <button type="button" className="btn secondary small" onClick={handleLogout}>Log out</button>
          </div>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      {toast && (
        <div className="toast" role="status">
          <div>{toast.message}</div>
          <div className="actions" style={{ marginTop: 8 }}>
            {toast.rideId && <Link to={`/rides/${toast.rideId}`} onClick={dismissToast}>Open ride</Link>}
            <button type="button" className="btn link" style={{ color: '#cfd6e6' }} onClick={dismissToast}>Dismiss</button>
          </div>
        </div>
      )}
    </>
  );
}
