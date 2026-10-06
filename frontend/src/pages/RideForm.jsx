import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { ErrorAlert } from '../components/Feedback.jsx';
import PlacePicker from '../components/PlacePicker.jsx';
import { formatMoney, todayIso } from '../utils/format.js';
import { usePlaces } from '../utils/usePlaces.js';

const EMPTY = { date: '', time: '', totalSeats: 4, seatsForCreator: 1, totalFare: '', notes: '' };

/** Create a ride (/rides/new) or edit one of mine (/rides/:id/edit). */
export default function RideForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const places = usePlaces();
  const [source, setSource] = useState(null);
  const [destination, setDestination] = useState(null);
  const [form, setForm] = useState({ ...EMPTY, date: todayIso() });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!editing) return;
    api.ride(id).then((detail) => {
      const r = detail.ride;
      setSource({ name: r.sourceName, latitude: r.sourceLatitude, longitude: r.sourceLongitude });
      setDestination({ name: r.destinationName, latitude: r.destinationLatitude, longitude: r.destinationLongitude });
      setForm({
        date: r.departureAt.slice(0, 10),
        time: r.departureAt.slice(11, 16),
        totalSeats: r.totalSeats,
        seatsForCreator: 1,
        totalFare: r.totalFare,
        notes: detail.notes || '',
      });
    }).catch(setError);
  }, [editing, id]);

  const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const perSeatPreview = () => {
    const fare = Number(form.totalFare);
    const seats = Number(form.totalSeats);
    return fare > 0 && seats > 0 ? formatMoney((fare / seats).toFixed(2)) : null;
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!source || !destination) {
      setError({ message: 'Choose both a pickup point and a destination.' });
      return;
    }
    setBusy(true);
    setError(null);
    const body = {
      sourceName: source.name,
      sourceLatitude: source.latitude,
      sourceLongitude: source.longitude,
      destinationName: destination.name,
      destinationLatitude: destination.latitude,
      destinationLongitude: destination.longitude,
      departureDate: form.date,
      departureTime: form.time,
      totalSeats: Number(form.totalSeats),
      totalFare: form.totalFare === '' ? null : Number(form.totalFare),
      notes: form.notes || null,
    };
    try {
      if (editing) {
        await api.updateRide(id, body);
        navigate(`/rides/${id}`);
      } else {
        const created = await api.createRide({ ...body, seatsForCreator: Number(form.seatsForCreator) });
        // Straight to suggestions: maybe someone is already going the same way.
        navigate(`/rides/${created.ride.id}/matches?new=1`);
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{editing ? 'Edit your ride' : 'Offer a ride'}</h1>
          <p>{editing
            ? 'Members are notified about any change.'
            : 'Tell others where and when you are going. You will see matching rides right after posting.'}</p>
        </div>
      </div>
      <form onSubmit={submit} className="panel">
        <ErrorAlert error={error} />
        <div className="grid-2">
          <PlacePicker id="source" label="Pickup" places={places} value={source} onChange={setSource} />
          <PlacePicker id="destination" label="Destination" places={places} value={destination}
                       onChange={setDestination} />
        </div>
        <div className="row">
          <div className="field">
            <label htmlFor="date">Date</label>
            <input id="date" type="date" min={todayIso()} value={form.date} onChange={update('date')} required />
          </div>
          <div className="field">
            <label htmlFor="time">Departure time</label>
            <input id="time" type="time" value={form.time} onChange={update('time')} required />
          </div>
          <div className="field">
            <label htmlFor="totalSeats">Passenger seats in the cab</label>
            <input id="totalSeats" type="number" min={2} max={7} value={form.totalSeats}
                   onChange={update('totalSeats')} required />
          </div>
          {!editing && (
            <div className="field">
              <label htmlFor="seatsForCreator">Seats you need</label>
              <input id="seatsForCreator" type="number" min={1} max={Math.max(1, form.totalSeats - 1)}
                     value={form.seatsForCreator} onChange={update('seatsForCreator')} required />
            </div>
          )}
          <div className="field">
            <label htmlFor="totalFare">Expected total fare (₹)</label>
            <input id="totalFare" type="number" min={0} step="1" value={form.totalFare}
                   onChange={update('totalFare')} required />
            {perSeatPreview() && <span className="hint">About {perSeatPreview()} each when the cab is full</span>}
          </div>
        </div>
        <div className="field">
          <label htmlFor="notes">Notes (optional)</label>
          <textarea id="notes" maxLength={500} value={form.notes} onChange={update('notes')}
                    placeholder="Meeting point, luggage, flight or train time…" />
        </div>
        <div className="actions">
          <button className="btn" type="submit" disabled={busy}>
            {busy ? 'Saving…' : editing ? 'Save changes' : 'Post ride'}
          </button>
          <button className="btn secondary" type="button" onClick={() => navigate(-1)}>Cancel</button>
        </div>
      </form>
    </>
  );
}
