import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { Empty, ErrorAlert, Pager } from '../components/Feedback.jsx';
import { RideRow } from '../components/RideVisuals.jsx';

export default function MyRides() {
  const [scope, setScope] = useState('upcoming');
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((pageNumber = 0) => {
    setError(null);
    api.myRides({ scope, page: pageNumber, size: 10 }).then(setPage).catch(setError);
  }, [scope]);

  useEffect(() => {
    load(0);
  }, [load]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>My rides</h1>
          <p>Rides you created or joined.</p>
        </div>
        <Link to="/rides/new" className="btn">Offer a ride</Link>
      </div>
      <div className="tabs" role="group" aria-label="Which rides">
        <button type="button" aria-pressed={scope === 'upcoming'} onClick={() => setScope('upcoming')}>Upcoming</button>
        <button type="button" aria-pressed={scope === 'past'} onClick={() => setScope('past')}>Past and cancelled</button>
      </div>
      <ErrorAlert error={error} />
      {page && page.content.length === 0 && (
        <Empty action={scope === 'upcoming' ? <Link to="/search" className="btn">Find a ride</Link> : null}>
          {scope === 'upcoming' ? 'You have no upcoming rides.' : 'No past rides yet.'}
        </Empty>
      )}
      <div className="ride-list">
        {page?.content.map((ride) => <RideRow key={ride.id} ride={ride} />)}
      </div>
      <Pager page={page} onChange={load} />
    </>
  );
}
