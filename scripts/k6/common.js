// Shared helpers for the k6 scripts. Run the backend with RATE_LIMIT_ENABLED=false
// first: setup() registers many test students from one IP.
import http from 'k6/http';

export const API = __ENV.API || 'http://localhost:8080';
export const DOMAIN = __ENV.DOMAIN || 'iitbbs.ac.in';
const PASSWORD = 'LoadTest123';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** Registers `count` fresh students in parallel batches and returns their access tokens. */
export function registerStudents(prefix, count) {
  const tokens = [];
  for (let start = 0; start < count; start += 20) {
    const batch = [];
    for (let i = start; i < Math.min(start + 20, count); i += 1) {
      batch.push(['POST', `${API}/api/auth/register`, JSON.stringify({
        name: `Load ${prefix} ${i}`,
        email: `${prefix}.${i}@${DOMAIN}`,
        password: PASSWORD,
      }), { headers: JSON_HEADERS, tags: { name: 'setup-register' } }]);
    }
    for (const res of http.batch(batch)) {
      if (res.status === 429) throw new Error('429 while registering: restart the backend with RATE_LIMIT_ENABLED=false');
      if (res.status !== 201) throw new Error(`register failed: ${res.status} ${res.body}`);
      tokens.push(res.json('accessToken'));
    }
  }
  return tokens;
}

export function auth(token, extra = {}) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, ...extra };
}

export function tomorrow() {
  const d = new Date(Date.now() + 24 * 60 * 60 * 1000);
  return d.toISOString().slice(0, 10);
}

export function createRide(token, { time = '09:30', seats = 4, offset = 0 } = {}) {
  const res = http.post(`${API}/api/rides`, JSON.stringify({
    sourceName: 'IIT Bhubaneswar',
    sourceLatitude: 20.1484 + offset,
    sourceLongitude: 85.6706 + offset,
    destinationName: 'Airport',
    destinationLatitude: 20.2444,
    destinationLongitude: 85.8178,
    departureDate: tomorrow(),
    departureTime: time,
    totalSeats: seats,
    totalFare: '600.00',
  }), auth(token, { tags: { name: 'setup-create-ride' } }));
  if (res.status !== 201) throw new Error(`create ride failed: ${res.status} ${res.body}`);
  return res.json('ride.id');
}

export function runId() {
  return `k6${Date.now().toString(36)}`;
}
