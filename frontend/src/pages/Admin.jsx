import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorAlert, Pager, Loading } from '../components/Feedback.jsx';
import { formatDateTime, formatMoney, statusLabel } from '../utils/format.js';

const RIDE_STATUSES = ['', 'OPEN', 'FULL', 'STARTED', 'COMPLETED', 'CANCELLED'];

export default function Admin() {
  const [tab, setTab] = useState('overview');
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Admin</h1>
          <p>Moderate accounts, review reports and watch activity.</p>
        </div>
      </div>
      <div className="tabs" role="group" aria-label="Admin sections">
        {[['overview', 'Overview'], ['users', 'Users'], ['rides', 'Rides'], ['reports', 'Reports']].map(([key, label]) => (
          <button key={key} type="button" aria-pressed={tab === key} onClick={() => setTab(key)}>{label}</button>
        ))}
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'users' && <Users />}
      {tab === 'rides' && <Rides />}
      {tab === 'reports' && <Reports />}
    </>
  );
}

function Overview() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    api.adminStats().then(setStats).catch(setError);
  }, []);
  if (error) return <ErrorAlert error={error} />;
  if (!stats) return <Loading label="Loading admin data…" />;
  const tiles = [
    [stats.totalUsers, 'registered users'],
    [stats.activeUsers, 'active users'],
    [stats.totalRides, 'rides posted'],
    [stats.upcomingActiveRides, 'upcoming open or full rides'],
    [stats.openReports, 'reports to review'],
    [formatMoney(stats.estimatedSavingsOnCompletedRides), 'estimated saved by sharing'],
  ];
  return (
    <>
      <div className="stat-grid">
        {tiles.map(([value, label]) => (
          <div className="stat" key={label}><strong>{value}</strong><span>{label}</span></div>
        ))}
      </div>
      <section className="panel" style={{ marginTop: 20 }}>
        <h2>Rides by status</h2>
        <table>
          <tbody>
            {Object.entries(stats.ridesByStatus).map(([status, count]) => (
              <tr key={status}><td>{statusLabel(status)}</td><td className="num">{count}</td></tr>
            ))}
          </tbody>
        </table>
        <p className="muted small" style={{ marginTop: 10 }}>
          Savings estimate: for each completed ride, (riders minus one) times the fare.
        </p>
      </section>
    </>
  );
}

function Users() {
  const { user: me } = useAuth();
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((pageNumber = 0) => {
    api.adminUsers({ query, page: pageNumber, size: 20 }).then(setPage).catch(setError);
  }, [query]);

  useEffect(() => {
    load(0);
    // Only on first render; searching is explicit.
  }, []);

  const toggle = async (u) => {
    const verb = u.active ? 'Deactivate' : 'Re-activate';
    if (!window.confirm(`${verb} ${u.email}?`)) return;
    try {
      await api.adminSetActive(u.id, !u.active);
      load(page?.page || 0);
    } catch (e) {
      setError(e);
    }
  };

  return (
    <section className="panel">
      <form className="actions" style={{ marginBottom: 14 }} onSubmit={(e) => { e.preventDefault(); load(0); }}>
        <input aria-label="Search users" placeholder="Name or email" value={query}
               onChange={(e) => setQuery(e.target.value)} style={{ maxWidth: 320 }} />
        <button type="submit" className="btn secondary">Search</button>
      </form>
      <ErrorAlert error={error} />
      <div className="table-wrap">
        <table>
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th /></tr></thead>
          <tbody>
            {page?.content.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>{u.role === 'ADMIN' ? 'Admin' : 'Student'}</td>
                <td>{u.active ? 'Active' : <span className="status CANCELLED">Deactivated</span>}</td>
                <td className="small muted">{formatDateTime(u.createdAt)}</td>
                <td className="num">
                  {u.id !== me.id && (
                    <button type="button" className={`btn small ${u.active ? 'danger' : 'secondary'}`}
                            onClick={() => toggle(u)}>
                      {u.active ? 'Deactivate' : 'Re-activate'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} onChange={load} />
    </section>
  );
}

function Rides() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((pageNumber = 0) => {
    api.adminRides({ status, page: pageNumber, size: 20 }).then(setPage).catch(setError);
  }, [status]);

  useEffect(() => {
    load(0);
  }, [load]);

  return (
    <section className="panel">
      <div className="field" style={{ maxWidth: 240 }}>
        <label htmlFor="ride-status">Status</label>
        <select id="ride-status" value={status} onChange={(e) => setStatus(e.target.value)}>
          {RIDE_STATUSES.map((s) => <option key={s} value={s}>{s ? statusLabel(s) : 'All'}</option>)}
        </select>
      </div>
      <ErrorAlert error={error} />
      <div className="table-wrap">
        <table>
          <thead><tr><th>Departure</th><th>Route</th><th>Creator</th><th className="num">Seats</th><th>Status</th></tr></thead>
          <tbody>
            {page?.content.map(({ ride, creatorEmail }) => (
              <tr key={ride.id}>
                <td>{formatDateTime(ride.departureAt)}</td>
                <td><Link to={`/rides/${ride.id}`}>{ride.sourceName} to {ride.destinationName}</Link></td>
                <td className="small">{creatorEmail}</td>
                <td className="num">{ride.occupiedSeats}/{ride.totalSeats}</td>
                <td>{statusLabel(ride.status)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} onChange={load} />
    </section>
  );
}

function Reports() {
  const [status, setStatus] = useState('OPEN');
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((pageNumber = 0) => {
    api.adminReports({ status, page: pageNumber, size: 20 }).then(setPage).catch(setError);
  }, [status]);

  useEffect(() => {
    load(0);
  }, [load]);

  const decide = async (report, decision) => {
    try {
      await api.adminUpdateReport(report.id, decision);
      load(page?.page || 0);
    } catch (e) {
      setError(e);
    }
  };

  const deactivate = async (report) => {
    if (!window.confirm(`Deactivate ${report.reportedUserEmail}?`)) return;
    try {
      await api.adminSetActive(report.reportedUserId, false);
      await api.adminUpdateReport(report.id, 'RESOLVED');
      load(page?.page || 0);
    } catch (e) {
      setError(e);
    }
  };

  return (
    <section className="panel">
      <div className="tabs" role="group" aria-label="Report status">
        {[['OPEN', 'Open'], ['RESOLVED', 'Resolved'], ['DISMISSED', 'Dismissed'], ['', 'All']].map(([key, label]) => (
          <button key={label} type="button" aria-pressed={status === key} onClick={() => setStatus(key)}>{label}</button>
        ))}
      </div>
      <ErrorAlert error={error} />
      {page && page.content.length === 0 && <p className="muted">No reports here.</p>}
      {page?.content.map((r) => (
        <div key={r.id} className="notification">
          <div>
            <div className="text">
              <strong>{r.reason.replaceAll('_', ' ').toLowerCase()}</strong>: {r.reportedUserEmail}
              {!r.reportedUserActive && <span className="status CANCELLED">Deactivated</span>}
            </div>
            {r.description && <p style={{ margin: '4px 0' }}>{r.description}</p>}
            <div className="when-small">
              Reported by {r.reporterEmail} on {formatDateTime(r.createdAt)}
              {r.rideId && <> for <Link to={`/rides/${r.rideId}`}>ride #{r.rideId}</Link></>}
            </div>
          </div>
          {r.status === 'OPEN' && (
            <div className="actions">
              <button type="button" className="btn small secondary" onClick={() => decide(r, 'DISMISSED')}>Dismiss</button>
              <button type="button" className="btn small secondary" onClick={() => decide(r, 'RESOLVED')}>Resolve</button>
              {r.reportedUserActive && (
                <button type="button" className="btn small danger" onClick={() => deactivate(r)}>Deactivate user</button>
              )}
            </div>
          )}
        </div>
      ))}
      <Pager page={page} onChange={load} />
    </section>
  );
}
