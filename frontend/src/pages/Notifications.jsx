import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { useRealtime } from '../realtime/RealtimeContext.jsx';
import { Empty, ErrorAlert, Pager } from '../components/Feedback.jsx';
import { formatDateTime } from '../utils/format.js';

export default function Notifications() {
  const { unread, setUnread, refreshUnread } = useRealtime();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((pageNumber = 0) => {
    api.notifications({ unreadOnly, page: pageNumber, size: 20 }).then(setPage).catch(setError);
  }, [unreadOnly]);

  // Reload when filters change or a new notification arrives live.
  useEffect(() => {
    load(0);
  }, [load, unread]);

  const markRead = async (notification) => {
    try {
      await api.markRead(notification.id);
      setUnread((count) => Math.max(0, count - 1));
    } catch (e) {
      setError(e);
    }
  };

  const markAll = async () => {
    try {
      await api.markAllRead();
      refreshUnread();
      load(0);
    } catch (e) {
      setError(e);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Notifications</h1>
          <p>Joins, leaves, cancellations and rides similar to yours.</p>
        </div>
        <div className="actions">
          <button type="button" className="btn secondary" onClick={markAll} disabled={unread === 0}>
            Mark all as read
          </button>
        </div>
      </div>
      <div className="tabs" role="group" aria-label="Filter">
        <button type="button" aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)}>All</button>
        <button type="button" aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)}>Unread</button>
      </div>
      <ErrorAlert error={error} />
      <section className="panel">
        {page && page.content.length === 0 && <Empty>You're all caught up.</Empty>}
        {page?.content.map((n) => (
          <div key={n.id} className={`notification${n.read ? '' : ' unread'}`}>
            <div>
              <div className="text">{n.message}</div>
              <div className="when-small">{formatDateTime(n.createdAt)}</div>
            </div>
            <div className="actions">
              {n.rideId && (
                <Link to={`/rides/${n.rideId}`} onClick={() => !n.read && markRead(n)}>Open ride</Link>
              )}
              {!n.read && (
                <button type="button" className="btn link" onClick={() => markRead(n)}>Mark read</button>
              )}
            </div>
          </div>
        ))}
        <Pager page={page} onChange={load} />
      </section>
    </>
  );
}
