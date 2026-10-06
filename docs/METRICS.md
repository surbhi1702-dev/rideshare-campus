# Measuring RideShare Campus yourself

Everything here runs on your own machine. The numbers depend on that machine, so write down **what your run
prints** in the table at the end, together with the hardware, and quote them that way (for example "on my
laptop, single instance"). Nothing in this repository claims a number you have not measured.

## 0. Prerequisites

- PostgreSQL running with the `rideshare` and `rideshare_test` databases (see the README).
- `jq` and `curl` for the race demo, [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) for the load
  tests (`brew install k6`, or the installer for your OS).

## 1. Test count and coverage (backend)

```bash
cd backend
mvn verify
```

At the end Maven prints `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`. That N is your backend test count.

Coverage report: open `backend/target/site/jacoco/index.html` in a browser. The **Total** row shows instruction
and branch coverage. Note both numbers; "line coverage" is not what JaCoCo shows at the top.

## 2. Test count (frontend)

```bash
cd frontend
npm install
npm test
```

Vitest prints `Tests  N passed`.

## 3. Start the backend for the race demo and load tests

The scripts register many test students from one IP, which the login/register rate limit would block (that is
the limit doing its job). Start a separate run with it switched off, against your local database:

```bash
cd backend
mvn -q -DskipTests package
RATE_LIMIT_ENABLED=false DB_USERNAME=<your db user> DB_PASSWORD=<your db password> \
  JWT_SECRET=$(openssl rand -base64 48) java -jar target/rideshare-campus.jar
```

The scripts create users named `race…@iitbbs.ac.in` / `k6…@iitbbs.ac.in` and a few rides. Use a local database
you don't mind filling with test data. Restart normally (without `RATE_LIMIT_ENABLED=false`) afterwards.

## 4. Race demo: no over-booking

```bash
./scripts/race-demo.sh                    # 30 students, 4-seat ride (3 free seats)
JOINERS=100 SEATS=6 ./scripts/race-demo.sh
```

It ends with `PASS: no over-booking, exactly X of N got a seat.` and the JOINED / RIDE_FULL counts. Run it a few
times (say 10) and note how many runs passed.

The automated version of the same check is `ConcurrentSeatAllocationIntegrationTest` in the backend suite.

## 5. k6: join race under load

```bash
k6 run scripts/k6/join-race.js                    # 50 virtual users, one join each, same ride
k6 run -e VUS=200 -e SEATS=5 scripts/k6/join-race.js
```

The run fails (red ✗, non-zero exit code) if more than `SEATS-1` joins succeed or any response is something other
than 200 / 409 `RIDE_FULL`. Note `joins_succeeded`, `joins_rejected_full` and `http_req_duration` p(95) for the
`join` requests.

## 6. k6: search and ride-detail load

```bash
k6 run scripts/k6/search-load.js                          # 20 virtual users for 30 s
k6 run -e VUS=50 -e DURATION=1m scripts/k6/search-load.js
```

Read from the summary:

| What | Where in the k6 output |
|---|---|
| p95 latency of search | `http_req_duration{name:search}` → `p(95)` |
| p95 latency of ride detail | `http_req_duration{name:ride-detail}` → `p(95)` |
| Throughput | `http_reqs` → the `/s` value |
| Error rate | `http_req_failed` |

Each virtual user waits 0.5–1 s between searches like a person would, so throughput here reflects that pacing; it
is not the server's maximum. The thresholds in the script (p95 < 500 ms, errors < 1 %) are pass/fail targets I
set, not results.

## 7. Record your numbers

| Metric | Your value | Command |
|---|---|---|
| Machine | `<CPU, RAM, OS>` | |
| Backend tests | `<N>` passing | `mvn verify` |
| Frontend tests | `<N>` passing | `npm test` |
| Backend instruction coverage | `<x %>` | JaCoCo report |
| Backend branch coverage | `<x %>` | JaCoCo report |
| Race demo | `<passed runs>` / `<runs>`, `<N>` joiners for `<S-1>` seats | `race-demo.sh` |
| k6 join race | `<N>` VUs, `<S-1>` succeeded, p95 `<ms>` | `join-race.js` |
| k6 search | `<VUS>` VUs, p95 `<ms>`, `<req/s>`, errors `<x %>` | `search-load.js` |

## Wording these on a resume

Keep the context next to the number: "single instance on my laptop", "with k6", "N concurrent join requests". A
number from your own run with its conditions is easy to defend in an interview; a round number without them is
not.
