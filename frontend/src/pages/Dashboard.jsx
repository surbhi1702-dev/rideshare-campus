import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useRealtime } from '../realtime/RealtimeContext.jsx';
import { Empty, ErrorAlert, Loading } from '../components/Feedback.jsx';
import { RideRow } from '../components/RideVisuals.jsx';

export default function Dashboard() {
  const { user } = useAuth();
  const { unread } = useRealtime();
  const [mine, setMine] = useState(null);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setError(null);
    Promise.all([api.myRides({ scope: 'upcoming', size: 3 }), api.browseRides({ size: 5 })])
      .then(([myPage, openPage]) => {
        setMine(myPage);
        setOpen(openPage);
      })
      .catch(setError);
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  const loading = !mine && !error;

  const firstName = user?.name?.split(' ')[0];

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Hi {firstName}, where are you headed?</h1>
          <p>Post your trip or join someone going the same way.</p>
        </div>
        <div className="actions">
          <Link to="/rides/new" className="btn">Offer a ride</Link>
          <Link to="/search" className="btn secondary">Find a ride</Link>
        </div>
      </div>

      <ErrorAlert error={error} onRetry={load} />

      {unread > 0 && (
        <div className="alert success">
          You have {unread} unread notification{unread > 1 ? 's' : ''}. <Link to="/notifications">View them</Link>
        </div>
      )}

      <section className="panel">
        <h2>Your upcoming rides</h2>
        {loading && <Loading label="Loading your rides…" />}
        {mine && mine.content.length === 0 && (
          <Empty action={<Link to="/rides/new" className="btn">Offer a ride</Link>}>
            No upcoming rides yet. Post your trip and we will suggest students to share with.
          </Empty>
        )}
        <div className="ride-list">
          {mine?.content.map((ride) => <RideRow key={ride.id} ride={ride} />)}
        </div>
        {mine?.totalElements > 3 && <p style={{ marginTop: 12 }}><Link to="/my-rides">See all my rides</Link></p>}
      </section>

      <section className="panel">
        <h2>Leaving soon</h2>
        {loading && <Loading label="Loading open rides…" />}
        {open && open.content.length === 0 && <Empty>No open rides right now.</Empty>}
        <div className="ride-list">
          {open?.content.map((ride) => <RideRow key={ride.id} ride={ride} />)}
        </div>
        {open?.totalElements > 5 && <p style={{ marginTop: 12 }}><Link to="/search">Search all rides</Link></p>}
      </section>
    </>
  );
}
