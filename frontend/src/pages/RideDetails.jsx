import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useRealtime } from '../realtime/RealtimeContext.jsx';
import { ErrorAlert, SuccessAlert } from '../components/Feedback.jsx';
import { RouteStrip, SeatDots, StatusPill, When } from '../components/RideVisuals.jsx';
import { formatDateTime, formatMoney } from '../utils/format.js';

const REPORT_REASONS = [
  ['NO_SHOW', 'Did not show up'],
  ['UNSAFE_BEHAVIOUR', 'Unsafe behaviour'],
  ['HARASSMENT', 'Harassment'],
  ['FARE_DISPUTE', 'Fare dispute'],
  ['FAKE_PROFILE', 'Fake profile'],
  ['OTHER', 'Something else'],
];

export default function RideDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { subscribeToRide } = useRealtime();
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [seats, setSeats] = useState(1);
  const [reporting, setReporting] = useState(null);

  const load = useCallback(() => api.ride(id).then(setDetail).catch(setError), [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Live: someone joins/leaves or the creator changes the ride -> refetch through REST.
  useEffect(() => subscribeToRide(id, () => load()), [id, subscribeToRide, load]);

  const run = async (action, successMessage, { confirmText, after } = {}) => {
    if (confirmText && !window.confirm(confirmText)) return;
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      const updated = await action();
      if (updated) setDetail(updated);
      setNotice(successMessage);
      after?.();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  if (!detail) return error ? <ErrorAlert error={error} /> : <p className="muted">Loading ride…</p>;

  const { ride, actions, participants, fareSplit } = detail;
  const isMember = Boolean(detail.viewerRole);

  return (
    <>
      <p className="small"><Link to="/my-rides">My rides</Link></p>
      <section className="ride-hero">
        <div>
          <When at={ride.departureAt} />
          <div style={{ marginTop: 12 }}>
            <RouteStrip from={ride.sourceName} to={ride.destinationName} />
          </div>
          <p className="small" style={{ margin: '10px 0 0', color: '#cfd6e6' }}>
            Posted by {ride.creator.displayName}
            <StatusPill status={ride.status} />
          </p>
        </div>
        <div className="seat-block">
          <SeatDots total={ride.totalSeats} occupied={ride.occupiedSeats} />
          <span className="seat-label">{ride.occupiedSeats} of {ride.totalSeats} seats taken</span>
          <div className="share" style={{ marginTop: 8 }}>{formatMoney(ride.totalFare)}</div>
          <span className="seat-label">expected total fare</span>
        </div>
      </section>

      <ErrorAlert error={error} />
      <SuccessAlert>{notice}</SuccessAlert>

      <div className="grid-2">
        <section className="panel">
          <h2>Fare split</h2>
          {fareSplit ? (
            <>
              <p style={{ fontSize: '1.25rem', margin: '0 0 4px' }}>
                Your share: <strong>{formatMoney(fareSplit.yourShare)}</strong>
              </p>
              <p className="muted small">
                {formatMoney(fareSplit.sharePerSeat)} per seat with {fareSplit.occupiedSeats} seats filled. The more
                people join, the less everyone pays. Payment is settled among yourselves.
              </p>
            </>
          ) : (
            <p>
              {detail.estimatedShareIfJoined
                ? <>If you join now you pay about <strong>{formatMoney(detail.estimatedShareIfJoined)}</strong>.</>
                : 'No seats left to join.'}
            </p>
          )}
          {detail.notes && (
            <>
              <h3>Notes from the creator</h3>
              <p>{detail.notes}</p>
            </>
          )}
        </section>

        <section className="panel">
          <h2>What you can do</h2>
          <div className="actions">
            {actions.canJoin && (
              <>
                <label htmlFor="join-seats" className="small">Seats</label>
                <select id="join-seats" value={seats} onChange={(e) => setSeats(Number(e.target.value))}
                        style={{ width: 70 }}>
                  {Array.from({ length: ride.availableSeats }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <button type="button" className="btn" disabled={busy}
                        onClick={() => run(() => api.joinRide(id, { seats }), 'You joined this ride.')}>
                  Join ride
                </button>
              </>
            )}
            {isMember && (
              <Link to={`/rides/${id}/matches`} className="btn secondary">See similar rides</Link>
            )}
            {actions.canEdit && <Link to={`/rides/${id}/edit`} className="btn secondary">Edit</Link>}
            {actions.canStart && (
              <button type="button" className="btn secondary" disabled={busy}
                      onClick={() => run(() => api.startRide(id), 'Marked as on the way.')}>Start ride</button>
            )}
            {actions.canComplete && (
              <button type="button" className="btn secondary" disabled={busy}
                      onClick={() => run(() => api.completeRide(id), 'Ride completed.')}>Mark completed</button>
            )}
            {actions.canLeave && (
              <button type="button" className="btn danger" disabled={busy}
                      onClick={() => run(() => api.leaveRide(id), 'You left the ride. Your seat is free again.',
                        { confirmText: 'Leave this ride? Your seat will be offered to others.' })}>
                Leave ride
              </button>
            )}
            {actions.canCancel && (
              <button type="button" className="btn danger" disabled={busy}
                      onClick={() => run(() => api.cancelRide(id), 'Ride cancelled. Members have been notified.',
                        { confirmText: 'Cancel this ride for everyone? All members will be notified.' })}>
                Cancel ride
              </button>
            )}
          </div>
          {!Object.values(actions).some(Boolean) && !isMember && (
            <p className="muted small" style={{ marginTop: 12 }}>This ride can no longer be joined.</p>
          )}
        </section>
      </div>

      <section className="panel">
        <h2>Group ({detail.participantCount} {detail.participantCount === 1 ? 'person' : 'people'})</h2>
        {!isMember && (
          <p className="muted">Names and phone numbers of the group are shown only to members.</p>
        )}
        {isMember && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Phone</th>
                  <th className="num">Seats</th>
                  <th className="num">Share</th>
                  <th>Joined</th>
                  <th><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {participants.map((p) => (
                  <tr key={p.userId}>
                    <td>
                      {p.name}
                      {p.role === 'CREATOR' && <span className="status">Creator</span>}
                      {p.userId === user.id && <span className="muted small"> (you)</span>}
                    </td>
                    <td>{p.phoneNumber ? <a href={`tel:${p.phoneNumber}`}>{p.phoneNumber}</a> : <span className="muted">Not shared</span>}</td>
                    <td className="num">{p.seatsBooked}</td>
                    <td className="num">{formatMoney(p.estimatedShare)}</td>
                    <td className="small muted">{formatDateTime(p.joinedAt)}</td>
                    <td>
                      {p.userId !== user.id && (
                        <div className="actions">
                          <button type="button" className="btn link small" onClick={() => setReporting(p)}>Report</button>
                          <button type="button" className="btn link small"
                                  onClick={() => run(() => api.block(p.userId).then(() => null),
                                    `${p.name} is blocked. You won't be matched with them again.`,
                                    { confirmText: `Block ${p.name}? You will not see each other's rides.` })}>
                            Block
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {reporting && (
        <ReportForm rideId={ride.id} person={reporting} onDone={(message) => {
          setReporting(null);
          if (message) setNotice(message);
        }} />
      )}

      {ride.status === 'CANCELLED' && (
        <p><button type="button" className="btn" onClick={() => navigate('/search')}>Find another ride</button></p>
      )}
    </>
  );
}

function ReportForm({ rideId, person, onDone }) {
  const [reason, setReason] = useState('NO_SHOW');
  const [description, setDescription] = useState('');
  const [error, setError] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    try {
      await api.report({ reportedUserId: person.userId, rideId, reason, description: description || null });
      onDone('Report sent. An administrator will review it.');
    } catch (e) {
      setError(e);
    }
  };

  return (
    <form className="panel" onSubmit={submit}>
      <h2>Report {person.name}</h2>
      <ErrorAlert error={error} />
      <div className="field">
        <label htmlFor="reason">What happened?</label>
        <select id="reason" value={reason} onChange={(e) => setReason(e.target.value)}>
          {REPORT_REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor="description">Details (optional)</label>
        <textarea id="description" maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="actions">
        <button type="submit" className="btn">Send report</button>
        <button type="button" className="btn secondary" onClick={() => onDone(null)}>Cancel</button>
      </div>
    </form>
  );
}
