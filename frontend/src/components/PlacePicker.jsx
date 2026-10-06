import { useState } from 'react';

const GEOCODER = import.meta.env.VITE_GEOCODER_URL || 'https://nominatim.openstreetmap.org/search';

/**
 * Pick a location: one of the campus presets from the backend, or any place
 * found through the free OpenStreetMap geocoder (no API key, no paid maps).
 * `value` is { name, latitude, longitude } or null.
 */
export default function PlacePicker({ label, places, value, onChange, id }) {
  const [mode, setMode] = useState('preset');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  const selectedPreset = places.findIndex(
    (p) => value && p.name === value.name && p.latitude === value.latitude && p.longitude === value.longitude,
  );

  const search = async () => {
    if (query.trim().length < 3) {
      setSearchError('Type at least 3 characters.');
      return;
    }
    setSearching(true);
    setSearchError('');
    try {
      const url = `${GEOCODER}?format=json&limit=5&countrycodes=in&q=${encodeURIComponent(query.trim())}`;
      const response = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error();
      const data = await response.json();
      setResults(data);
      if (data.length === 0) setSearchError('No places found. Try a nearby landmark.');
    } catch {
      setSearchError('Place search is unavailable right now. Pick a preset instead.');
    } finally {
      setSearching(false);
    }
  };

  const choose = (result) => {
    const shortName = result.display_name.split(',').slice(0, 2).join(',').trim();
    onChange({ name: shortName.slice(0, 120), latitude: Number(result.lat), longitude: Number(result.lon) });
    setResults([]);
  };

  return (
    <fieldset>
      <legend>{label}</legend>
      <div className="tabs" role="group" aria-label={`${label} input mode`}>
        <button type="button" aria-pressed={mode === 'preset'} onClick={() => setMode('preset')}>Common places</button>
        <button type="button" aria-pressed={mode === 'search'} onClick={() => setMode('search')}>Search a place</button>
      </div>

      {mode === 'preset' ? (
        <div className="field">
          <label htmlFor={`${id}-preset`}>Place</label>
          <select id={`${id}-preset`} value={selectedPreset >= 0 ? selectedPreset : ''}
                  onChange={(e) => onChange(e.target.value === '' ? null : places[Number(e.target.value)])}>
            <option value="">Choose…</option>
            {places.map((p, i) => <option key={p.name} value={i}>{p.name}</option>)}
          </select>
        </div>
      ) : (
        <div className="field">
          <label htmlFor={`${id}-search`}>Search OpenStreetMap</label>
          <div className="actions" style={{ flexWrap: 'nowrap' }}>
            <input id={`${id}-search`} value={query} onChange={(e) => setQuery(e.target.value)}
                   onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); search(); } }}
                   placeholder="e.g. Kalinga Stadium Bhubaneswar" />
            <button type="button" className="btn secondary" onClick={search} disabled={searching}>
              {searching ? 'Searching…' : 'Search'}
            </button>
          </div>
          {searchError && <span className="field-error">{searchError}</span>}
          {results.length > 0 && (
            <ul className="place-results">
              {results.map((r) => (
                <li key={r.place_id}>
                  <button type="button" onClick={() => choose(r)}>{r.display_name}</button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {value && (
        <p className="small muted">
          Selected: <strong>{value.name}</strong> ({value.latitude.toFixed(4)}, {value.longitude.toFixed(4)})
        </p>
      )}
    </fieldset>
  );
}
