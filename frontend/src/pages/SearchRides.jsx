import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { Empty, ErrorAlert, Loading, Pager } from '../components/Feedback.jsx';
import PlacePicker from '../components/PlacePicker.jsx';
import { RideRow } from '../components/RideVisuals.jsx';
import { todayIso } from '../utils/format.js';
import { usePlaces } from '../utils/usePlaces.js';

export default function SearchRides() {
  const [tab, setTab] = useState('match');
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Find a ride</h1>
          <p>Match by where and when you travel, or browse every open ride.</p>
        </div>
      </div>
      <div className="tabs" role="group" aria-label="Search mode">
        <button type="button" aria-pressed={tab === 'match'} onClick={() => setTab('match')}>Match my trip</button>
        <button type="button" aria-pressed={tab === 'browse'} onClick={() => setTab('browse')}>Browse open rides</button>
      </div>
      {tab === 'match' ? <MatchSearch /> : <Browse />}
    </>
  );
}

function MatchSearch() {
  const places = usePlaces();
  const [source, setSource] = useState(null);
  const [destination, setDestination] = useState(null);
  const [form, setForm] = useState({ date: todayIso(), time: '', seats: 1 });
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (!source || !destination) {
      setError({ message: 'Choose both a pickup point and a destination.' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setResults(await api.searchRides({
        sourceLatitude: source.latitude,
        sourceLongitude: source.longitude,
        destinationLatitude: destination.latitude,
        destinationLongitude: destination.longitude,
        date: form.date,
        time: form.time,
        seats: form.seats,
      }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <form className="panel" onSubmit={submit}>
        <ErrorAlert error={error} />
        <div className="grid-2">
          <PlacePicker id="s-source" label="Pickup" places={places} value={source} onChange={setSource} />
          <PlacePicker id="s-destination" label="Destination" places={places} value={destination}
                       onChange={setDestination} />
        </div>
        <div className="row">
          <div className="field">
            <label htmlFor="s-date">Date</label>
            <input id="s-date" type="date" value={form.date} min={todayIso()}
                   onChange={(e) => setForm({ ...form, date: e.target.value })} required />
          </div>
          <div className="field">
            <label htmlFor="s-time">Around what time?</label>
            <input id="s-time" type="time" value={form.time}
                   onChange={(e) => setForm({ ...form, time: e.target.value })} required />
          </div>
          <div className="field">
            <label htmlFor="s-seats">Seats needed</label>
            <input id="s-seats" type="number" min={1} max={6} value={form.seats}
                   onChange={(e) => setForm({ ...form, seats: e.target.value })} required />
          </div>
        </div>
        <button className="btn" type="submit" disabled={busy}>{busy ? 'Searching…' : 'Find matching rides'}</button>
      </form>

      {results && results.length === 0 && (
        <Empty action={<Link to="/rides/new" className="btn">Offer this ride</Link>}>
          Nobody is going that way around that time yet. Post the ride and you will be notified when someone similar
          posts one.
        </Empty>
      )}
      {results && results.length > 0 && <MatchList matches={results} />}
    </>
  );
}

/** Ranked matches with the "why" in plain words. Also used by the Matches page. */
export function MatchList({ matches, renderAction }) {
  return (
    <div className="ride-list">
      {matches.map((m) => (
        <div key={m.ride.id}>
          <RideRow
            ride={m.ride}
            share={m.estimatedShareIfJoined}
            shareLabel="your share if you join"
            extra={<span className="score">{m.match.compatibilityPercent}% match</span>}
          />
          <div className="match-meta" style={{ padding: '4px 4px 0' }}>
            Pickup {m.match.pickupDistanceKm} km from yours, drop {m.match.destinationDistanceKm} km from yours,
            {' '}{m.match.timeDifferenceMinutes} min apart. Distances are straight-line, not by road.
            {renderAction && <span style={{ marginLeft: 10 }}>{renderAction(m)}</span>}
          </div>
        </div>
      ))}
    </div>
  );
}

function Browse() {
  const [filters, setFilters] = useState({ source: '', destination: '', date: '', minSeats: '' });
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const load = async (pageNumber = 0) => {
    setError(null);
    setLoading(true);
    try {
      setPage(await api.browseRides({ ...filters, page: pageNumber, size: 10 }));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(0);
    // First page of all open rides on arrival; later loads follow the filters.
  }, []);

  const update = (key) => (e) => setFilters({ ...filters, [key]: e.target.value });

  return (
    <>
      <form className="panel" onSubmit={(e) => { e.preventDefault(); load(0); }}>
        <ErrorAlert error={error} />
        <div className="row">
          <div className="field">
            <label htmlFor="b-source">From (name contains)</label>
            <input id="b-source" value={filters.source} onChange={update('source')} placeholder="Campus" />
          </div>
          <div className="field">
            <label htmlFor="b-destination">To (name contains)</label>
            <input id="b-destination" value={filters.destination} onChange={update('destination')}
                   placeholder="Airport" />
          </div>
          <div className="field">
            <label htmlFor="b-date">Date</label>
            <input id="b-date" type="date" value={filters.date} onChange={update('date')} />
          </div>
          <div className="field">
            <label htmlFor="b-seats">Free seats at least</label>
            <input id="b-seats" type="number" min={1} value={filters.minSeats} onChange={update('minSeats')} />
          </div>
        </div>
        <button className="btn" type="submit" disabled={loading}>{loading ? 'Loading…' : 'Show rides'}</button>
      </form>
      {loading && !page && <Loading label="Loading open rides…" />}
      {page && page.content.length === 0 && <Empty>No open rides match these filters.</Empty>}
      <div className="ride-list">
        {page?.content.map((ride) => <RideRow key={ride.id} ride={ride} />)}
      </div>
      <Pager page={page} onChange={load} />
    </>
  );
}
