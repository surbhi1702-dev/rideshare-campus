// Write-path race: VUS students each send one join for the same ride at once.
//
//   k6 run scripts/k6/join-race.js
//   k6 run -e VUS=100 -e SEATS=5 scripts/k6/join-race.js
//
// The ride has SEATS-1 free seats. Exactly that many joins must succeed, every other
// one must get 409 RIDE_FULL, and occupiedSeats must never exceed totalSeats. The
// run fails (non-zero exit) if any of that is not true.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { API, auth, createRide, registerStudents, runId } from './common.js';

const VUS = Number(__ENV.VUS || 50);
const SEATS = Number(__ENV.SEATS || 4);

// 409 RIDE_FULL is the correct answer for most joiners here, not a failure.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const joined = new Counter('joins_succeeded');
const rejectedFull = new Counter('joins_rejected_full');
const unexpected = new Counter('joins_unexpected');

export const options = {
  setupTimeout: '180s',
  scenarios: {
    race: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '60s' },
  },
  thresholds: {
    checks: ['rate==1'],
    joins_unexpected: ['count==0'],
    joins_succeeded: [`count==${SEATS - 1}`],
  },
};

export function setup() {
  const tokens = registerStudents(runId(), VUS + 1);
  const rideId = createRide(tokens[0], { seats: SEATS });
  return { rideId, creator: tokens[0], joiners: tokens.slice(1) };
}

export default function (data) {
  const token = data.joiners[__VU - 1];
  const res = http.post(`${API}/api/rides/${data.rideId}/join`, JSON.stringify({ seats: 1 }),
    auth(token, { tags: { name: 'join' } }));
  if (res.status === 200) joined.add(1);
  else if (res.status === 409 && res.json('code') === 'RIDE_FULL') rejectedFull.add(1);
  else unexpected.add(1);
  check(res, { 'join answered 200 or 409 RIDE_FULL': (r) => r.status === 200 || r.status === 409 });
}

export function teardown(data) {
  const ride = http.get(`${API}/api/rides/${data.rideId}`, auth(data.creator)).json('ride');
  console.log(`ride ${data.rideId}: occupied ${ride.occupiedSeats} / ${ride.totalSeats}`);
  check(ride, {
    'no over-booking': (r) => r.occupiedSeats <= r.totalSeats,
    'ride ended up full': (r) => r.occupiedSeats === r.totalSeats,
  });
}
