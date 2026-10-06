// Read-path load test: students searching for matching rides and opening one.
//
//   k6 run scripts/k6/search-load.js
//   k6 run -e VUS=50 -e DURATION=1m scripts/k6/search-load.js
//
// Reports p95 latency (http_req_duration), throughput (http_reqs per second) and the
// error rate (http_req_failed). The thresholds below are targets that make the run
// pass or fail, not measured results: write down what your own run prints.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { API, auth, createRide, registerStudents, runId, tomorrow } from './common.js';

const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '30s';
const RIDES = Number(__ENV.RIDES || 20);

export const options = {
  setupTimeout: '120s',
  scenarios: {
    search: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: VUS },
        { duration: DURATION, target: VUS },
        { duration: '5s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:search}': ['p(95)<500'],
    'http_req_duration{name:ride-detail}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const prefix = runId();
  const tokens = registerStudents(prefix, RIDES + 5);
  const rideIds = [];
  // RIDES creators each post one ride near campus, spread over the morning.
  for (let i = 0; i < RIDES; i += 1) {
    const hour = 8 + (i % 4);
    const minute = (i * 7) % 60;
    const time = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    rideIds.push(createRide(tokens[i], { time, seats: 4, offset: (i % 5) * 0.002 }));
  }
  return { searchers: tokens.slice(RIDES), rideIds };
}

export default function (data) {
  const token = data.searchers[__VU % data.searchers.length];
  const hour = 8 + Math.floor(Math.random() * 4);
  const params = {
    sourceLatitude: 20.1484,
    sourceLongitude: 85.6706,
    destinationLatitude: 20.2444,
    destinationLongitude: 85.8178,
    date: tomorrow(),
    time: `${String(hour).padStart(2, '0')}:30`,
    seats: 1,
  };
  const query = Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
  const search = http.get(`${API}/api/rides/search?${query}`, auth(token, { tags: { name: 'search' } }));
  check(search, { 'search 200': (r) => r.status === 200 });

  const rideId = data.rideIds[Math.floor(Math.random() * data.rideIds.length)];
  const detail = http.get(`${API}/api/rides/${rideId}`, auth(token, { tags: { name: 'ride-detail' } }));
  check(detail, { 'detail 200': (r) => r.status === 200 });

  sleep(Math.random() * 0.5 + 0.5); // a person reads the results before the next search
}
