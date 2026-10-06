import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

let cache = null;

/** Preset pickup/drop points configured on the backend (fetched once per session). */
export function usePlaces() {
  const [places, setPlaces] = useState(cache || []);
  useEffect(() => {
    if (cache) return;
    api.places().then((list) => {
      cache = list;
      setPlaces(list);
    }).catch(() => setPlaces([]));
  }, []);
  return places;
}
