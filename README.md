# RideShare Campus: Smart Ride Matching System

A campus-only ride-sharing platform. Students post the trip they are about to take (campus to airport,
railway station, bus stand, city…). The system then finds other students going **roughly the same way at
roughly the same time**, forms a shared group, and splits the cab fare between the members.

> Built for a closed institute community such as IIT Bhubaneswar. Only institute email addresses can sign up.

---

## Contents

1. [Problem statement](#problem-statement)
2. [Features](#features)
3. [What changed in V2](#what-changed-in-v2)
4. [Architecture](#architecture)
5. [Technology stack (and why each piece is there)](#technology-stack)
6. [Project structure](#project-structure)
7. [Database design](#database-design)
8. [Matching algorithm](#matching-algorithm)
9. [Concurrency: the last-seat problem](#concurrency-the-last-seat-problem)
10. [Security architecture](#security-architecture)
11. [API](#api)
12. [Running locally](#running-locally)
13. [Running with Docker](#running-with-docker)
14. [Environment variables](#environment-variables)
15. [Testing](#testing)
16. [Deployment](#deployment)
17. [Sample users and rides](#sample-users-and-rides)
18. [Example API requests](#example-api-requests)
19. [Measuring it yourself](#measuring-it-yourself)
20. [Future scope](#future-scope)

---

## Problem statement

Campuses such as IIT Bhubaneswar are far from the airport, railway station and city. A solo cab is expensive, and
students already share rides informally through WhatsApp groups. That approach has clear problems:

- Messages get buried, so people miss each other.
- Nobody knows who is actually confirmed or how many seats are left.
- Two people can both believe they got the last seat.
- Splitting the fare is awkward.

RideShare Campus replaces that with a structured system. The main technical component is a **matching engine**
that ranks compatible rides by pickup distance, drop distance, time difference and group fit. Seat allocation is
**safe under concurrency**, so the last seat can only be taken once.

## Features

| Area | What is implemented |
|---|---|
| Accounts | Registration restricted to configurable institute domains, BCrypt passwords, roles `STUDENT` / `ADMIN`, profile edit, password change |
| Sessions | 15-minute access token kept in memory + refresh token in an httpOnly cookie, rotated on every use, with reuse detection |
| Rides | Create, view, edit (creator only), cancel, start, complete; status machine `OPEN → FULL → STARTED → COMPLETED`, `CANCELLED` |
| Matching | Ranked search for an ad-hoc trip (`/api/rides/search`) and "rides similar to mine" (`/api/rides/{id}/matches`), with an explainable score |
| Suggestions | Posting a ride notifies creators of compatible existing rides ("reverse matching") |
| Groups | Join with 1..n seats, leave, capacity enforcement, no duplicates, no joining cancelled/completed/started/departed rides |
| Waitlist | Queue for a full ride; a freed seat goes to the first waiter whose request fits, inside the same locked transaction |
| Safe retries | `Idempotency-Key` on join / waitlist so a retried request after a network drop never books twice |
| Rate limiting | Token bucket per IP on login/register and per user on join/waitlist → `429` + `Retry-After` |
| Merge | Join a better ride and cancel your own (empty) ride **in one transaction** |
| Overlap guard | A student cannot hold two active rides departing within 90 minutes of each other |
| Concurrency | Row-level locking on the ride; DB `CHECK` and `UNIQUE` constraints as a safety net |
| Fare split | Exact split in paise (shares always sum to the total); "your share if you join" preview |
| Notifications | Stored in-app notifications (joined, left, full, updated, cancelled, started, completed, match suggestion), read/unread, mark all read |
| Real-time | STOMP over WebSocket: live seat/status updates on the ride page and live notification badge/toast |
| Safety and privacy | Institute-only sign-up, "First L." display names for non-members, phone numbers visible only inside the same ride, block (symmetric), report (only people you shared a ride with) |
| Admin | Stats (users, rides by status, open reports, estimated savings), user search, deactivate/re-activate (takes effect immediately), ride list by status, report review |
| Places | Configurable campus presets plus free OpenStreetMap (Nominatim) place search; no paid map API |
| Frontend | Responsive layout (phone menu, stacked tables), loading and retry states, Vitest + Testing Library tests |
| Ops | Flyway migrations, Swagger UI, Docker/Compose, health check, JaCoCo coverage, GitHub Actions CI with a Render deploy step, Render + Vercel config |

## What changed in V2

V1 worked on one laptop. V2 is the set of changes I made to treat it more like something people would actually
use: problems that show up with real networks, real browsers and more than one user at a time. I kept it to what
one person can build and test properly, and left the rest as design notes (see [Future scope](#future-scope)).

| Problem | What V2 does | Where |
|---|---|---|
| A full ride just says "full", and a seat that frees up later goes to whoever refreshes first | Waitlist. When a seat frees (leave, seat count raised) the first waiter whose seat request fits is promoted **inside the same row lock** as the leave, so a promotion can never over-book. Waiters who became ineligible (overlapping ride, blocked, deactivated) are skipped and told. Cancel/start clears the queue. | `ride/waitlist/*`, `V2__ride_waitlist.sql` |
| Phone on campus Wi-Fi: join request reaches the server, response is lost, the app retries → "already joined" or a double booking | The client sends one `Idempotency-Key` per button press and retries only on network errors / 502-504. The key is stored in the **same transaction** as the join, so a retry replays the current result instead of booking again. Reusing a key for a different ride → `422`. Keys are purged after 24 h. | `common/idempotency/*`, `V3__idempotency_keys.sql`, `frontend/src/api/client.js` |
| Password guessing, scripts hammering join | In-memory token bucket: login/register per client IP (default 20/min), join/waitlist per user (default 30/min). `429 RATE_LIMITED` with `Retry-After`; the UI says how long to wait. Refresh is deliberately not limited because a whole hostel can share one NAT IP. | `common/ratelimit/*` |
| V1 kept a 24 h JWT in `localStorage`, readable by any XSS bug | 15-minute access token held only in memory; refresh token in an `httpOnly`, `SameSite=Lax` cookie scoped to `/api/auth`. Only a SHA-256 hash is stored. Each refresh rotates the token; presenting an already-rotated token later revokes the whole login (token family). Two tabs refreshing at the same moment is tolerated for 30 s. | `auth/session/*`, `V4__refresh_tokens.sql` |
| Unusable on a phone, blank screens while loading | Collapsible phone menu, tables that turn into cards under 640 px, spinners and "Try again" on every page. | `frontend/src/styles.css`, `components/Feedback.jsx` |
| "Works on my machine" | Frontend unit/component tests in CI, JaCoCo coverage report, Render (API + Postgres) and Vercel (frontend) config, deploy only after tests pass. | `.github/workflows/ci.yml`, `render.yaml`, `frontend/vercel.json`, `docs/DEPLOYMENT.md` |
| No way to show the concurrency handling actually works | A race-demo script and two k6 load tests you run yourself. | `scripts/`, [`docs/METRICS.md`](docs/METRICS.md) |

**Limits I know about.** Rate-limit buckets live in one JVM's memory, so they reset on restart and would not be
shared across several instances (Redis would fix that). Behind a proxy the client IP comes from
`X-Forwarded-For`, which is only as trustworthy as the proxy in front. The free Render tier sleeps when idle, so the
first request after a while is slow.

## Architecture

A **modular monolith**: one Spring Boot application, organised by feature module. Each module has its own
controller, service, repository, entity, DTO and mapper classes. There are no microservices, because a single
campus does not need them and a single database transaction is exactly what seat allocation requires.

```
                ┌──────────────── React + Vite (nginx) ────────────────┐
                │ pages ─ api/client.js (REST)  realtime/ (STOMP/WS)   │
                └───────────────┬──────────────────────┬───────────────┘
                     /api/** (JWT)             /ws (JWT on CONNECT)
┌───────────────────────────────▼──────────────────────▼──────────────────────────┐
│ Spring Boot                                                                     │
│  security/  JWT filter · STOMP auth interceptor · JSON 401/403                  │
│  auth/  user/  ride/  matching/  notification/  safety/  admin/  location/      │
│   controller → service → repository → entity         (DTOs + mappers at edges)  │
│  ride/RideLocker  = SELECT … FOR UPDATE          matching/MatchingEngine        │
│  common/exception  = one ApiError shape for every failure                       │
└───────────────────────────────┬─────────────────────────────────────────────────┘
                                │ JPA / Flyway
                        ┌───────▼────────┐
                        │ PostgreSQL 16  │
                        └────────────────┘
```

**Layering rules the code follows**
- Controllers only translate HTTP to service calls. There is no business logic in them.
- Services own transactions (`@Transactional`) and business rules.
- `RidePolicy` is the single place that answers "may user X do action Y on this ride now?". Services throw its
  answer as an error, and the detail endpoint turns the same checks into `actions.canJoin/canLeave/...` flags
  for the UI.
- Entities never leave the service layer; every response is a DTO (Java `record`).

## Technology stack

| Technology | Why it is here |
|---|---|
| Java 21, Spring Boot 3.4 | Web, validation, DI, transactions |
| Spring Data JPA / Hibernate | Persistence; schema is **validated**, not generated |
| PostgreSQL 16 | Row-level locks, CHECK constraints, partial indexes |
| Flyway | Versioned, reviewable schema (`db/migration/V1__init_schema.sql`) |
| Spring Security + JJWT | Stateless JWT authentication, role-based authorization |
| Spring WebSocket (STOMP) | Pushes exactly two events: ride seat/status changes and new notifications |
| springdoc-openapi | Swagger UI and the OpenAPI document |
| Spring Boot Actuator | `/actuator/health` only, used by the Docker health check |
| JUnit 5, Mockito, AssertJ, MockMvc | Unit, mock and integration tests |
| React 18, Vite, React Router, @stomp/stompjs | Frontend |
| Docker, Docker Compose, nginx | Packaging and a single-origin reverse proxy |
| GitHub Actions | Runs the full test suite against a real PostgreSQL on every push |

**Deliberately left out**
- **Redis.** Nothing needs caching yet. The hot query (matching) is an indexed range scan over a small time
  window. Adding Redis would add a second source of truth for seat counts, which is the exact thing we must
  not have.
- **Microservices, Kafka.** They bring no benefit at campus scale.
- **Paid map APIs.** Haversine plus presets plus the free Nominatim geocoder are enough for V1.

## Project structure

```
rideshare-campus/
├── docker-compose.yml             # db + backend + frontend
├── .env.example                   # all configuration, no real secrets
├── .github/workflows/ci.yml       # tests against PostgreSQL + frontend build
├── backend/
│   ├── Dockerfile, pom.xml
│   └── src/
│       ├── main/java/com/rideshare/
│       │   ├── RideShareApplication.java
│       │   ├── config/            AppProperties, ClockConfig, OpenApiConfig, WebSocketConfig
│       │   ├── security/          SecurityConfig, JwtService, JwtAuthenticationFilter, TokenAuthenticator,
│       │   │                      StompAuthChannelInterceptor, AuthenticatedUser, JSON 401/403 handlers
│       │   ├── common/            exception/ (ErrorCode, ApiError, GlobalExceptionHandler, …), dto/ (PageResponse, Paging)
│       │   ├── auth/              AuthController, AuthService, dto/
│       │   ├── user/              User, Role, UserRepository, UserService, UserController, EmailDomainValidator,
│       │   │                      AdminAccountInitializer, UserMapper, dto/
│       │   ├── ride/              Ride, RideParticipant, RideStatus, RideRepository, RideParticipantRepository,
│       │   │                      RideLocker, RideService, RideParticipationService, RidePolicy, RideValidator,
│       │   │                      OverlapGuard, FareCalculator, RideMapper, RideSpecifications, RideController, dto/
│       │   ├── matching/          MatchingEngine, MatchScorer, MatchingProperties, MatchQuery, MatchScore,
│       │   │                      MatchSuggestionNotifier, MatchingService, MatchController, geo/ (Haversine, BoundingBox)
│       │   ├── notification/      Notification, NotificationService, RealtimePublisher, NotificationController, dto/
│       │   ├── safety/            UserBlock, Report, BlockLookup, SafetyService, SafetyController, dto/
│       │   ├── admin/             AdminService, AdminController, dto/
│       │   ├── location/          PlacesProperties, PlaceController
│       │   └── demo/              DemoDataSeeder (only with the "demo" profile)
│       ├── main/resources/        application.yml, application-demo.yml, db/migration/V1__init_schema.sql
│       └── test/                  unit tests, Mockito tests, integration tests (incl. concurrency)
└── frontend/
    ├── Dockerfile, nginx.conf, vite.config.js, package.json, index.html
    └── src/
        ├── api/client.js          fetch wrapper + every endpoint
        ├── auth/AuthContext.jsx   token + current user
        ├── realtime/              STOMP connection, live notifications, ride subscriptions
        ├── components/            Layout, Guards, RideVisuals (route strip, seat dots), PlacePicker, Feedback
        ├── pages/                 Login, Register, Dashboard, RideForm (create/edit), SearchRides, RideDetails,
        │                          Matches, MyRides, Notifications, Profile, Admin
        └── utils/                 date/money formatting, places hook
```

## Database design

```
users ──< rides (creator_id)
users ──< ride_participants >── rides        UNIQUE (ride_id, user_id)
users ──< notifications                      (ride_id → rides, SET NULL)
users ──< user_blocks >── users              UNIQUE (blocker_id, blocked_id), CHECK blocker <> blocked
users ──< reports >── users                  (ride_id → rides, SET NULL)
```

| Table | Purpose / notable constraints |
|---|---|
| `users` | `email` unique, `role` in (STUDENT, ADMIN), `active` flag for moderation |
| `rides` | Route (names + lat/lng), `departure_at`, `total_seats`, **`occupied_seats`** (denormalised), `total_fare` ≥ 0, `status`. `CHECK (occupied_seats BETWEEN 0 AND total_seats)` |
| `ride_participants` | One row per member **including the creator** (`role` CREATOR/MEMBER), `seats_booked` > 0, unique per (ride, user) |
| `notifications` | Inbox rows with `is_read` |
| `user_blocks` | Directed pair; the effect is applied in both directions |
| `reports` | Reason, description, status OPEN/RESOLVED/DISMISSED |

**Why `occupied_seats` is denormalised.** Search and matching filter on free seats for many rides at once.
Summing participants for each candidate would be a join plus an aggregate on the hottest query. The counter is
only ever changed while the ride row is locked, and the invariant `occupied_seats = SUM(seats_booked)` is
asserted by the concurrency tests.

### Indexes

| Index | Serves |
|---|---|
| `idx_rides_status_departure (status, departure_at)` | Matching and browsing: `status = 'OPEN' AND departure_at BETWEEN …` (verified with `EXPLAIN`: index scan) |
| `idx_rides_source_coords (source_latitude, source_longitude)` | Bounding-box pre-filter on pickup points |
| `idx_rides_creator (creator_id)` | Rides by creator (admin, ownership) |
| `uk_participant_ride_user (ride_id, user_id)` | Duplicate-join protection **and** "members of ride X" lookups |
| `idx_participants_user (user_id)` | "My rides", overlap guard, block checks |
| `idx_notifications_recipient_created (recipient_id, created_at DESC)` | Inbox, newest first |
| `idx_notifications_recipient_unread` **partial** `WHERE is_read = FALSE` | Unread badge count stays cheap as history grows |
| `idx_user_blocks_blocked (blocked_id)` | "Who blocked me" (the other direction is covered by the unique key) |
| `idx_reports_status_created (status, created_at DESC)` | Admin report queue |
| `uk_users_email` | Login and registration lookups |

## Matching algorithm

Code: `matching/MatchingEngine.java`, `matching/MatchScorer.java`, `matching/geo/*`.

A **query** is: requester, pickup point, drop point, desired departure time, seats needed.
It is built either from a search form or from one of the requester's rides.

### 1. Candidate retrieval (SQL, indexed)
`RideRepository.findMatchCandidates` returns only rides that:
- are `OPEN` and depart within `±MAX_TIME_DIFFERENCE_MINUTES` of the query (and in the future),
- have at least `seatsNeeded` free seats,
- have their pickup inside a lat/lng **bounding box** of radius `MAX_PICKUP_DISTANCE_KM` around the query pickup,
  and their drop inside a box of radius `MAX_DESTINATION_DISTANCE_KM`,
- do not already contain the requester (which also excludes their own rides),
- do not contain anyone the requester blocked or who blocked the requester.

The bounding box is a cheap, indexable superset of the circle. For a radius `r`:
`Δlat = r / 111.32`, `Δlng = r / (111.32 · cos(lat))`.

### 2. Elimination (exact)
For each candidate the **Haversine** great-circle distance is computed for pickup and for drop, along with the absolute
time difference. Anything beyond a limit is dropped (the box corners are outside the circle).

> **Haversine is straight-line ("as the crow flies") distance, not road distance.** Roads are typically 1.2–1.5×
> longer, and a river or a highway without a crossing can make two "close" points far apart by road. It is used
> here only to decide whether two pickup points (or two drop points) are near each other. Within a few kilometres
> that approximation is reasonable and free. `DistanceCalculator` is an interface, so a routing-engine
> implementation (OSRM, GraphHopper) can replace it without touching the engine.

### 3. Scoring (normalised weighted cost; lower is better)

```
p = pickupKm        / MAX_PICKUP_DISTANCE_KM          ∈ [0, 1]
d = destinationKm   / MAX_DESTINATION_DISTANCE_KM     ∈ [0, 1]
t = |Δ minutes|     / MAX_TIME_DIFFERENCE_MINUTES     ∈ [0, 1]
g = 1 − (occupied + seatsNeeded) / totalSeats         ∈ [0, 1)

score = (wp·p + wd·d + wt·t + wg·g) / (wp + wd + wt + wg)        ∈ [0, 1]
compatibilityPercent = round(100 · (1 − score))
```

- **Normalisation.** Each raw value is divided by its own tolerance, so kilometres and minutes both become "the
  fraction of what the student is willing to accept". Without this, minutes (0–60) would swamp kilometres (0–3).
  The same constants act as hard cut-offs, so every component lives in [0, 1] and the score is comparable
  across searches.
- **Weights** (defaults, configurable): destination 0.35 > pickup 0.30 > time 0.25 > group 0.10.
  The drop point weighs most because a different destination means a detour for everyone. The pickup is
  next, since walking a little on campus is cheap. Time comes after that because people can usually shift by
  15–30 minutes. The group term `g` is a tie-breaker that prefers groups closer to full, since those are cheaper
  per head and more likely to actually happen.
- **Group compatibility** also appears as hard rules: enough free seats for `seatsNeeded`, and no blocked
  relationship with *any* current member.
- **Ranking**: by score, ties broken by the smaller time difference, then by ride id, so results are
  deterministic. The top `MATCH_MAX_RESULTS` (default 20) are returned together with every component, so the UI
  can explain *why* a ride matched.

### Complexity
Let `C` be the number of candidates returned by step 1. Steps 2–3 are `O(C)` with constant work per ride, and
ranking is `O(C log C)`. `C` is small in practice because the composite index limits rows to one status and a
two-hour window, and the bounding boxes cut it further. Only `C` rows ever leave the database.

### Why this approach
It is explainable (every match shows its distances and minutes), tunable without code changes (environment
variables), and cheap (one indexed query plus arithmetic). Exact string matching of place names would miss
"Hostel Gate" versus "Main Gate", which are 300 m apart. Full route optimisation (vehicle routing with pickups
along the way) is a much harder problem and is listed as future scope.

### Reverse matching (suggestions)
When a ride is created, the engine runs with the new ride as the query. The creators of compatible rides
(up to `MATCH_SUGGESTION_LIMIT`) receive a `MATCH_SUGGESTION` notification. Two half-empty cabs can then merge,
using "Merge into this ride", which joins theirs and cancels yours atomically.

## Concurrency: the last-seat problem

Scenario: a ride has 4 seats and 3 are taken. Students A and B press **Join** at the same moment. Exactly one
may succeed.

### Chosen strategy: pessimistic row lock (`SELECT … FOR UPDATE`)
Every operation that changes a ride's seats, status or membership (join, leave, update, cancel, start,
complete) begins with `RideLocker.lock(rideId)`:

```java
entityManager.createNativeQuery("select set_config('lock_timeout', :timeout, true)")   // SET LOCAL
entityManager.find(Ride.class, rideId, LockModeType.PESSIMISTIC_WRITE);                // SELECT … FOR UPDATE
```

1. A locks the ride row. B's `SELECT … FOR UPDATE` **waits**.
2. A checks the rules, inserts its participant row, sets `occupied_seats = 4` and `status = FULL`, then commits.
3. B's lock is granted. PostgreSQL returns the **committed** row (4/4, FULL), so B fails the rule check
   with `409 RIDE_FULL`. B never computes its decision from stale data.

This behaviour was verified on PostgreSQL 16: the second session blocked until the first committed and then
read the new value.

**Why this strategy rather than the alternatives**
- *Optimistic locking (`@Version`).* It is correct, but under contention (which is exactly what the last seat
  is) the loser gets a version conflict and must retry the whole business check, so the retry logic moves into
  the client or a retry loop. A join also checks several things at once: status, departure time, duplicates,
  blocks, overlap and seats. Serialising them under one lock is simpler to reason about.
- *Atomic `UPDATE … SET occupied = occupied + n WHERE occupied + n <= total`.* That is perfect for a bare
  counter, but it cannot express the other rules, and the participant insert still has to be coordinated with
  the counter.
- *Cost.* The lock covers one ride row for a few milliseconds. Different rides never block each other.
  `lock_timeout` (default 3 s, `RIDE_LOCK_TIMEOUT_MS`) turns a pathological wait into `409 CONCURRENT_UPDATE`
  instead of a hung request.
- *Merge (join plus cancel own ride)* locks two rides, always **in ascending id order**, so two opposite merges
  cannot deadlock.

**Database safety nets** (in case application logic is ever bypassed):
- `CHECK (occupied_seats >= 0 AND occupied_seats <= total_seats)`: an over-booked row cannot be stored.
- `UNIQUE (ride_id, user_id)`: a double-click can never create two memberships.

**Tests** (`ConcurrentSeatAllocationIntegrationTest`, real PostgreSQL, one thread and one transaction per student,
released together by a latch):
- 2 students race for the last seat: exactly 1 succeeds and the other gets `RIDE_FULL`. This is repeated 5 times.
- 10 students race for 3 seats: exactly 3 succeed and 7 get `RIDE_FULL`.
- The same student double-clicks: 1 success and 1 `ALREADY_JOINED`.
- After each race, `occupied_seats` equals `SUM(seats_booked)` in the database and never exceeds capacity.

## Security architecture

- **Authentication.** `POST /api/auth/login` checks the BCrypt hash and returns a short-lived (15 min) HMAC-SHA256
  JWT that holds only the user id and role, and sets the refresh cookie. Unknown email and wrong password give the same message and take the same time (a
  dummy BCrypt check runs), so accounts cannot be enumerated.
- **Sessions (V2).**
  - The access token is kept in a JavaScript variable only; a page reload gets a new one from
    `POST /api/auth/refresh` using the `rs_refresh` cookie (`HttpOnly`, `SameSite=Lax`, `Path=/api/auth`,
    `Secure` in production).
  - The database stores only the SHA-256 of each refresh token. Every refresh rotates it (row-locked, so two
    concurrent refreshes cannot both win).
  - Reuse detection: if a token that was rotated more than 30 s ago comes back, someone else had a copy, so the
    whole token family is revoked and both parties must log in again.
  - Logout revokes the family and clears the cookie. Deactivated users cannot refresh.
  - CSRF: the cookie is only sent to `/api/auth`, `SameSite=Lax` blocks cross-site POSTs, and `/refresh` and
    `/logout` additionally require an `X-Requested-With` header that a plain HTML form cannot set.
- **Per-request check.** `JwtAuthenticationFilter` verifies the signature, issuer and expiry, then loads the user and
  requires `active = true`. Deactivating a user therefore locks them out **immediately**, even with a valid token.
- **Registration.** Only emails in `ALLOWED_EMAIL_DOMAINS` (subdomains allowed; look-alikes such as
  `fakeiitbbs.ac.in` are rejected). Admins cannot self-register; the first admin is created from
  `ADMIN_EMAIL` and `ADMIN_PASSWORD` on startup.
- **Authorization.**
  - `/api/admin/**` is protected twice: by a URL rule and by `@PreAuthorize("hasRole('ADMIN')")`.
  - Ownership is enforced in `RidePolicy`: only the creator may edit, cancel, start or complete a ride.
  - Seat counts cannot be manipulated: the client never sends a count, and the server derives it from locked
    state.
  - Notifications are looked up by (id, recipient), so other users' notifications are simply "not found".
  - Match suggestions for a ride are visible to its members only.
- **Privacy.**
  - Non-members see the creator as "First L.", a member count and the estimated share.
  - Names and phone numbers of the group are returned only to members of that ride.
  - Password hashes never leave the service layer (entities are never serialised).
- **Safety tools.**
  - Blocking is symmetric in effect: blocked pairs never see each other's rides in search, browse or matching,
    and cannot join a ride that contains the other. The error message never reveals who blocked whom.
  - Reports are only allowed against someone you shared a ride with.
- **WebSocket.**
  - The handshake is public, but the STOMP `CONNECT` frame must carry the same JWT.
  - Clients may subscribe only to `/user/queue/**` (their own queue) and `/topic/rides/{id}`.
  - `SEND` is rejected, so the socket is push-only.
  - Ride-topic events carry no personal data (only seat counts and status); clients refetch through the
    authorised REST endpoint.
  - Events are sent **after commit**, so nobody is told about a join that was rolled back.
- **Transport and config.**
  - Normal API calls authenticate with the `Authorization` header, not cookies, so they need no CSRF token. The
    only cookie endpoints are covered as described under Sessions.
  - CORS is limited to `CORS_ALLOWED_ORIGINS`, and nginx serves everything from one origin in production.
  - Secrets come only from environment variables. `JWT_SECRET` has no default, so the application refuses to
    start without one.
- **Errors.** Every failure is an `ApiError { status, code, message, fieldErrors }`. SQL, stack traces and class
  names are logged, never returned.
- **Abuse.** Rate limits on login/register (per IP) and join/waitlist (per user); see [What changed in V2](#what-changed-in-v2).

## API

Interactive documentation: **`/swagger-ui.html`** (OpenAPI JSON at `/v3/api-docs`). Click **Authorize** and paste
the token from login.

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register (institute email) → 201 + token |
| POST | `/api/auth/login` | public | Log in → access token + refresh cookie |
| POST | `/api/auth/refresh` | refresh cookie | New access token, rotates the cookie (needs `X-Requested-With`) |
| POST | `/api/auth/logout` | refresh cookie | End the session, clear the cookie → 204 |
| GET / PUT | `/api/users/me` | student | My profile / update name and phone |
| PUT | `/api/users/me/password` | student | Change password → 204 |
| GET | `/api/places` | student | Preset pickup/drop points |
| POST | `/api/rides` | student | Create ride → 201 + `Location` |
| GET | `/api/rides` | student | Browse open rides (`source`, `destination`, `date`, `fromTime`, `toTime`, `minSeats`, `page`, `size`) |
| GET | `/api/rides/mine?scope=upcoming\|past` | student | Rides I created or joined |
| GET | `/api/rides/search` | student | **Ranked matches** for a trip (`sourceLatitude`, `sourceLongitude`, `destinationLatitude`, `destinationLongitude`, `date`, `time`, `seats`) |
| GET | `/api/rides/{id}` | student | Ride detail (privacy depends on membership) |
| PUT | `/api/rides/{id}` | creator | Update ride |
| GET | `/api/rides/{id}/matches` | member | Rides similar to this one |
| POST | `/api/rides/{id}/join` | student | Join (`{ "seats": 1, "replaceRideId": null }`), optional `Idempotency-Key` header |
| POST | `/api/rides/{id}/waitlist` | student | Join the waitlist of a full ride (`{ "seats": 1 }`), optional `Idempotency-Key` |
| DELETE | `/api/rides/{id}/waitlist` | waiter | Leave the waitlist |
| POST | `/api/rides/{id}/leave` | member | Leave, freeing the seats |
| POST | `/api/rides/{id}/cancel` | creator | Cancel; members are notified |
| POST | `/api/rides/{id}/start` · `/complete` | creator | Status transitions |
| GET | `/api/notifications?unreadOnly=` | student | Inbox (paged) |
| GET | `/api/notifications/unread-count` | student | Badge count |
| PATCH | `/api/notifications/{id}/read` · `/read-all` | student | Mark read |
| GET / POST / DELETE | `/api/users/me/blocks[/{userId}]` | student | List / block / unblock |
| POST | `/api/reports` | student | Report a co-rider |
| GET | `/api/admin/stats` | admin | Statistics |
| GET | `/api/admin/users?query=` | admin | Users |
| PATCH | `/api/admin/users/{id}/deactivate` · `/activate` | admin | Moderation |
| GET | `/api/admin/rides?status=` | admin | Rides |
| GET / PATCH | `/api/admin/reports[/{id}]` | admin | Review reports |

**API design notes**
- State changes with side effects (join, leave, cancel, start, complete) are explicit `POST` actions rather than
  overloaded `DELETE`/`PATCH` calls.
- Rides are **never hard-deleted**. There is intentionally no `DELETE /api/rides/{id}`: cancelling keeps the
  history that members, reports and statistics need.
- Lists are paginated with a `PageResponse { content, page, size, totalElements, totalPages }` envelope, and page
  size is capped at 100.

**Status codes**: `200`, `201` (create/register/block/report), `204` (no body), `400` validation or business-rule
input, `401` not authenticated, `403` not allowed / deactivated / blocked, `404` not found, `409` state conflict
(`RIDE_FULL`, `ALREADY_JOINED`, `RIDE_CANCELLED`, `OVERLAPPING_RIDE`, `CONCURRENT_UPDATE`, `ALREADY_WAITLISTED`,
`SEATS_AVAILABLE`, `WAITLIST_FULL`, …), `422` `IDEMPOTENCY_KEY_REUSED`, `429` `RATE_LIMITED` (with `Retry-After`).

Error body:
```json
{
  "timestamp": "2026-10-05T04:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "RIDE_FULL",
  "message": "This ride is already full",
  "path": "/api/rides/12/join"
}
```

## Running locally

**Prerequisites:** Java 21, Maven 3.9+, Node 20+, PostgreSQL 16.

```bash
# 1. Database
createuser -P rideshare               # choose a password
createdb -O rideshare rideshare

# 2. Backend (http://localhost:8080, Swagger at /swagger-ui.html)
cd backend
export DB_PASSWORD='<the password>'
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_EMAIL=admin@iitbbs.ac.in ADMIN_PASSWORD='Admin12345'
export SPRING_PROFILES_ACTIVE=demo    # optional: sample students and rides
mvn spring-boot:run

# 3. Frontend (http://localhost:5173, proxies /api and /ws to :8080)
cd ../frontend
npm install
npm run dev
```

Flyway creates the schema on first start.

## Running with Docker

```bash
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD, JWT_SECRET (openssl rand -base64 48), ADMIN_PASSWORD
docker compose up --build
```

- App: http://localhost:3000. nginx serves React and proxies `/api`, `/ws` and Swagger to the backend.
- API directly: http://localhost:8080 (Swagger: http://localhost:8080/swagger-ui.html)
- Seed sample data: set `SPRING_PROFILES_ACTIVE=demo` in `.env` before the first start.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | built from `DB_HOST` / `DB_PORT` / `DB_NAME` (`localhost` / `5432` / `rideshare`) | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `rideshare` / **required** | DB credentials |
| `JWT_SECRET` | **required** | Base64 key, ≥ 256 bits |
| `JWT_EXPIRATION_MINUTES` | `15` | Access token lifetime |
| `REFRESH_TOKEN_DAYS` | `14` | Refresh cookie lifetime |
| `REFRESH_COOKIE_SECURE` | `false` | `true` when served over HTTPS |
| `REFRESH_COOKIE_SAME_SITE` | `Lax` | Refresh cookie SameSite |
| `REFRESH_REUSE_GRACE_SECONDS` | `30` | Window in which a just-rotated token is rejected without revoking the session |
| `FORWARD_HEADERS_STRATEGY` | `none` | `framework` behind a proxy so rate limiting sees the client IP |
| `RATE_LIMIT_ENABLED` | `true` | Turn off only for the race demo / load tests |
| `RATE_LIMIT_AUTH_PER_MINUTE` / `RATE_LIMIT_JOIN_PER_MINUTE` | `20` / `30` | Per IP (login, register) / per user (join, waitlist) |
| `RIDE_MAX_WAITLIST_SIZE` | `20` | Max waiters per ride |
| `ALLOWED_EMAIL_DOMAINS` | `iitbbs.ac.in` | Comma-separated registration domains |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Browser origins allowed to call the API/WebSocket |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | empty | Bootstrap admin (created once) |
| `APP_TIME_ZONE` | `Asia/Kolkata` | Zone in which departure times are interpreted |
| `MAX_PICKUP_DISTANCE_KM` | `3.0` | Matching: max pickup distance (straight line) |
| `MAX_DESTINATION_DISTANCE_KM` | `5.0` | Matching: max drop distance (straight line) |
| `MAX_TIME_DIFFERENCE_MINUTES` | `60` | Matching: max departure difference |
| `MATCH_WEIGHT_PICKUP` / `_DESTINATION` / `_TIME` / `_GROUP` | `0.30` / `0.35` / `0.25` / `0.10` | Score weights |
| `MATCH_MAX_RESULTS` | `20` | Matches returned |
| `MATCH_SUGGESTION_LIMIT` | `10` | Max owners notified per new ride |
| `RIDE_MAX_SEATS` | `7` | Max capacity of a ride |
| `RIDE_MAX_DAYS_AHEAD` | `60` | How far ahead rides can be planned |
| `RIDE_MIN_TRIP_DISTANCE_KM` | `0.5` | Rejects rides whose source ≈ destination |
| `RIDE_OVERLAP_WINDOW_MINUTES` | `90` | One active ride per student within this window |
| `RIDE_START_EARLY_WINDOW_MINUTES` | `60` | How early a ride may be marked started |
| `RIDE_LOCK_TIMEOUT_MS` | `3000` | Max wait for a ride row lock |
| `SPRING_PROFILES_ACTIVE` | empty | `demo` seeds sample data |
| `TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD` | `…/rideshare_test`, `postgres`, `postgres` | Integration-test database |

Campus preset places (name + coordinates) live in `application.yml` under `app.places`.

## Testing

Integration tests use a **real PostgreSQL**, because row locks and CHECK constraints are part of what they verify
and an in-memory database would not behave the same way.

```bash
createdb rideshare_test               # once (user/password default to postgres/postgres; override with TEST_DB_*)
cd backend
mvn verify
```

| Test class | Kind | Covers |
|---|---|---|
| `HaversineDistanceCalculatorTest` | unit | Known distances (London–Paris), symmetry, bounding box ⊇ circle |
| `MatchScorerTest` | unit | Normalisation, weights, pickup/destination/time cut-offs, seats, score range |
| `MatchingEngineTest` | Mockito | Ranking order, top-K, deterministic ties, time window passed to SQL, past trips skipped |
| `FareCalculatorTest` | unit | 600 / 3 = 200, exact paise split sums to the total, multi-seat shares |
| `RideValidatorTest` | unit | Past departure, too far ahead, seat limits, same source and destination |
| `RidePolicyTest` | unit | Join/leave/edit/cancel/start/complete rules, OPEN ↔ FULL transitions |
| `RideParticipationServiceTest` | Mockito | Last seat → FULL + notifications, block check, leave reopens the ride |
| `EmailDomainValidatorTest`, `StompAuthChannelInterceptorTest` | unit | Domain rules, allowed subscriptions |
| `AuthIntegrationTest` | integration | Register, domain check, duplicates, validation, login, 401 without a token, profile |
| `RideLifecycleIntegrationTest` | integration | Create, privacy, join, notifications, duplicate join, full, leave, cancel, update, overlap, merge, my rides |
| `MatchingSearchIntegrationTest` | integration | Ranked search, exclusions, matches for my ride, suggestions, browse filters, block, report |
| `AdminIntegrationTest` | integration | 403 for students, stats, immediate deactivation, report review |
| `ConcurrentSeatAllocationIntegrationTest` | **integration, concurrent** | Last-seat race, 10-for-3 race, double-click |
| `WaitlistIntegrationTest` | integration, concurrent | Position, promotion on leave / seat increase, first-fit, skipping ineligible waiters, cancel clears queue, three simultaneous leaves |
| `IdempotentJoinIntegrationTest` | integration, concurrent | Replay with the same key, key reuse → 422, bad key → 400, two copies of one request at once book once |
| `SessionIntegrationTest` | integration | Cookie attributes, rotation, two-tab grace, reuse revokes the family, logout, CSRF header, deactivated user |
| `RateLimiterTest`, `RateLimitIntegrationTest` | unit + integration | Token bucket refill, 429 + `Retry-After` |

Coverage: `mvn verify` also writes a JaCoCo report to `backend/target/site/jacoco/index.html`.

Frontend (Vitest + Testing Library, no backend needed):

```bash
cd frontend
npm install
npm test
```

They cover the API client (silent refresh, one shared refresh for parallel 401s, idempotent retries), the login
page and the ride page's waitlist buttons.

Both suites run in GitHub Actions (`.github/workflows/ci.yml`); the backend one against a PostgreSQL service
container.

## Deployment

**Free hosted demo (V2):** backend + PostgreSQL on Render (`render.yaml`), frontend on Vercel
(`frontend/vercel.json`). Vercel forwards `/api/*` to Render, so the refresh cookie stays first-party; the
WebSocket connects to Render directly. GitHub Actions deploys the backend only after both test jobs pass.
Step by step: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

**Own VM:** the Compose stack is production-shaped: a non-root JRE image with a health check, and nginx on a single
origin. For a real campus deployment:

1. **One VM** (for example AWS Lightsail/EC2 or any 1–2 vCPU / 2 GB machine): install Docker, copy the repository and
   `.env`, then run `docker compose up -d --build`.
2. Put **HTTPS** in front (Caddy, or nginx with Let's Encrypt), terminating TLS and forwarding to port 3000. The
   frontend automatically switches to `wss://`.
3. **Managed PostgreSQL** (such as RDS) is optional: point `DB_URL` at it and remove the `db` service. Enable automated
   backups.
4. Set a long random `JWT_SECRET`, a strong `ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS=https://your-domain`, and
   leave `SPRING_PROFILES_ACTIVE` empty.
5. Scaling beyond one backend instance: switch the STOMP simple broker to a broker relay (RabbitMQ) so pushes reach
   users on any instance. Row locking already works across instances because it lives in PostgreSQL.

## Sample users and rides

With `SPRING_PROFILES_ACTIVE=demo` and an empty database, `DemoDataSeeder` creates (through the real services):

| Name | Email | Password |
|---|---|---|
| Aarav Mishra | aarav@iitbbs.ac.in | `Password123` (or `DEMO_USER_PASSWORD`) |
| Priya Nayak | priya@iitbbs.ac.in | same |
| Rohan Das | rohan@iitbbs.ac.in | same |
| Sneha Patel | sneha@iitbbs.ac.in | same |
| Kabir Singh | kabir@iitbbs.ac.in | same |

Plus the admin from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

| Ride | Creator | When | Seats | Fare |
|---|---|---|---|---|
| Campus → Airport | Aarav (Priya joined) | tomorrow 09:00 | 2/4 | ₹900 |
| Campus → Railway Station | Rohan | tomorrow 17:30 | 1/4 | ₹750 |
| Hostel Gate → Master Canteen | Sneha | tomorrow 17:50 | 1/3 | ₹700 |
| Campus → Patia / KIIT Square | Kabir | day after tomorrow 10:00 | 1/5 | ₹1000 |

Rohan's and Sneha's rides are a deliberate near-match: pickups about 0.3 km apart, drops about 0.2 km apart, 20
minutes apart. Log in as Kabir and search Campus → Railway Station tomorrow at 17:40 to see both ranked.

## Example API requests

```bash
API=http://localhost:8080/api

# Register and keep the token
TOKEN=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Rahul Sharma","email":"rahul@iitbbs.ac.in","password":"Secret123","phoneNumber":"9876543210"}' \
  | jq -r .accessToken)

# Create a ride: campus → airport tomorrow 09:30, 4 seats, ₹800
curl -s -X POST $API/rides -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "sourceName":"IIT Bhubaneswar (Argul Campus)","sourceLatitude":20.1484,"sourceLongitude":85.6706,
  "destinationName":"Biju Patnaik International Airport","destinationLatitude":20.2444,"destinationLongitude":85.8178,
  "departureDate":"'"$(date -d tomorrow +%F)"'","departureTime":"09:30","totalSeats":4,"totalFare":800}'

# Ranked matches for a trip
curl -s "$API/rides/search?sourceLatitude=20.1484&sourceLongitude=85.6706&destinationLatitude=20.2444&destinationLongitude=85.8178&date=$(date -d tomorrow +%F)&time=09:00&seats=1" \
  -H "Authorization: Bearer $TOKEN" | jq '.[] | {id: .ride.id, pct: .match.compatibilityPercent, share: .estimatedShareIfJoined}'

# Join ride 1 (1 seat), or merge: join ride 1 and cancel my ride 5 in one step
curl -s -X POST $API/rides/1/join -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"seats":1}'
curl -s -X POST $API/rides/1/join -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"seats":1,"replaceRideId":5}'

# Leave / cancel
curl -s -X POST $API/rides/1/leave  -H "Authorization: Bearer $TOKEN"
curl -s -X POST $API/rides/5/cancel -H "Authorization: Bearer $TOKEN"

# Notifications
curl -s "$API/notifications?unreadOnly=true" -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH $API/notifications/3/read -H "Authorization: Bearer $TOKEN"
```

## Measuring it yourself

[`docs/METRICS.md`](docs/METRICS.md) explains how to run the test suites, the coverage report, the race demo and
the k6 load tests, and gives a table to record the numbers from your own machine. No numbers are quoted in this
README on purpose: they depend on the hardware they were measured on.

## Future scope

- Road-distance matching and "pick me up on the way" routing (OSRM/GraphHopper behind `DistanceCalculator`).
- Email OTP verification of the institute address (V1 validates the domain only).
- UPI payment requests and settlement tracking (no real payments, escrow or verification in V1).
- Redis-backed rate limiting so limits are shared by several backend instances.
- Metrics and dashboards (Micrometer + Prometheus/Grafana) instead of running k6 by hand.
- Recurring rides (every Friday to the station).
- Push notifications (web push / mobile), email digests.
- Demand insights (popular routes and times), and later ML-based suggestions.
- Live GPS sharing during a ride.
- Broker relay (RabbitMQ) for multi-instance real-time.
