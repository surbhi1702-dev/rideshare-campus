import { Link } from 'react-router-dom';
import { formatDate, formatMoney, formatTime, statusLabel } from '../utils/format.js';

/** Departure time set like a road sign: big time, small date. */
export function When({ at }) {
  return (
    <div className="when">
      <span className="time">{formatTime(at)}</span>
      <span className="date">{formatDate(at)}</span>
    </div>
  );
}

/** Pickup and drop joined by the route line. */
export function RouteStrip({ from, to }) {
  return (
    <div className="route" aria-label={`From ${from} to ${to}`}>
      <span className="pin from" aria-hidden="true" />
      <span className="stop" title={from}>{from}</span>
      <span className="pin to" aria-hidden="true" />
      <span className="stop" title={to}>{to}</span>
    </div>
  );
}

/** One circle per seat, filled when taken - reads like a cab's seat map. */
export function SeatDots({ total, occupied }) {
  const seats = Array.from({ length: total }, (_, i) => i < occupied);
  return (
    <span className="seats" role="img" aria-label={`${occupied} of ${total} seats taken`}>
      {seats.map((taken, i) => <span key={i} className={`seat${taken ? ' taken' : ''}`} />)}
    </span>
  );
}

export function StatusPill({ status }) {
  if (!status || status === 'OPEN') return null;
  return <span className={`status ${status}`}>{statusLabel(status)}</span>;
}

/**
 * A ride in a list. `extra` renders under the seat block (e.g. match score),
 * `share` overrides the displayed amount (e.g. "your share if you join").
 */
export function RideRow({ ride, share, shareLabel = 'per seat now', extra }) {
  const inactive = ride.status === 'CANCELLED' || ride.status === 'COMPLETED';
  return (
    <Link to={`/rides/${ride.id}`} className={`ride-row${inactive ? ' inactive' : ''}`}>
      <When at={ride.departureAt} />
      <div>
        <RouteStrip from={ride.sourceName} to={ride.destinationName} />
        <div className="small muted" style={{ marginTop: 4 }}>
          by {ride.creator?.displayName}
          <StatusPill status={ride.status} />
        </div>
      </div>
      <div className="seat-block">
        <SeatDots total={ride.totalSeats} occupied={ride.occupiedSeats} />
        <span className="seat-label">{ride.availableSeats} free</span>
        <div className="share">{formatMoney(share ?? ride.currentSharePerSeat)}</div>
        <span className="seat-label">{shareLabel}</span>
        {extra}
      </div>
    </Link>
  );
}
