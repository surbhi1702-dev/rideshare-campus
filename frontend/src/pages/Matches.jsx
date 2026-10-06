import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { Empty, ErrorAlert } from '../components/Feedback.jsx';
import { RouteStrip, When } from '../components/RideVisuals.jsx';
import { MatchList } from './SearchRides.jsx';

/**
 * Matching suggestions for one of my rides. If I'm alone in my ride I can
 * merge into a better one: join it and cancel mine in a single step.
 */
export default function Matches() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const justCreated = searchParams.get('new') === '1';
  const navigate = useNavigate();
  const [mine, setMine] = useState(null);
  const [matches, setMatches] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  useEffect(() => {
    Promise.all([api.ride(id), api.matchesForRide(id)])
      .then(([detail, list]) => {
        setMine(detail);
        setMatches(list);
      })
      .catch(setError);
  }, [id]);

  const canMerge = mine?.viewerRole === 'CREATOR' && mine.participantCount === 1;

  const merge = async (match) => {
    if (!window.confirm('Join this ride and cancel yours? Nobody else is in your ride yet.')) return;
    setBusyId(match.ride.id);
    setError(null);
    try {
      await api.joinRide(match.ride.id, { seats: mine.fareSplit ? seatsIHold(mine) : 1, replaceRideId: Number(id) });
      navigate(`/rides/${match.ride.id}`);
    } catch (e) {
      setError(e);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{justCreated ? 'Your ride is posted' : 'Similar rides'}</h1>
          <p>Rides leaving near your pickup, going near your destination, around the same time.</p>
        </div>
        <Link to={`/rides/${id}`} className="btn secondary">Go to my ride</Link>
      </div>

      {mine && (
        <div className="panel" style={{ display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap' }}>
          <When at={mine.ride.departureAt} />
          <RouteStrip from={mine.ride.sourceName} to={mine.ride.destinationName} />
        </div>
      )}

      <ErrorAlert error={error} />

      {matches && matches.length === 0 && (
        <Empty action={<Link to={`/rides/${id}`} className="btn">View my ride</Link>}>
          No similar rides yet. Students posting a similar trip will see yours, and you will be notified.
        </Empty>
      )}

      {matches && matches.length > 0 && (
        <>
          {canMerge && (
            <p className="muted">
              Found a better fit? Merge into it: you join that group and your own ride is cancelled in one step.
            </p>
          )}
          <MatchList
            matches={matches}
            renderAction={canMerge ? (m) => (
              <button type="button" className="btn small" disabled={busyId !== null} onClick={() => merge(m)}>
                {busyId === m.ride.id ? 'Merging…' : 'Merge into this ride'}
              </button>
            ) : undefined}
          />
        </>
      )}
    </>
  );
}

function seatsIHold(detail) {
  const me = detail.participants.find((p) => p.role === detail.viewerRole);
  return me ? me.seatsBooked : 1;
}
