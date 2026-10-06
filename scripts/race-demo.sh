#!/usr/bin/env bash
# Race demo: many students try to join the same ride at the same moment.
#
# It registers fresh test students, one of them creates a ride with a few free
# seats, and then every other student sends POST /api/rides/{id}/join in parallel.
# The ride must end up exactly full: (free seats) joins succeed, the rest get
# 409 RIDE_FULL, and occupiedSeats never goes above totalSeats.
#
# Needs: bash, curl, jq, and a running backend started with RATE_LIMIT_ENABLED=false
# (otherwise registering many students from one IP hits the login/register limit).
#
# Usage:
#   ./scripts/race-demo.sh                 # 30 joiners, 4-seat ride (3 free seats)
#   JOINERS=50 SEATS=6 ./scripts/race-demo.sh
#   API=http://localhost:8080 ./scripts/race-demo.sh
set -euo pipefail

API="${API:-http://localhost:8080}"
JOINERS="${JOINERS:-30}"
SEATS="${SEATS:-4}"          # total seats incl. the creator's own seat (2..10)
DOMAIN="${DOMAIN:-iitbbs.ac.in}"
PASSWORD="RaceDemo123"
RUN="$(date +%s)"            # makes the emails unique so the script can be re-run

command -v jq >/dev/null || { echo "Please install jq first (brew install jq / sudo apt install jq)."; exit 1; }
curl -fsS "$API/actuator/health" >/dev/null || { echo "Backend is not reachable at $API"; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

register() { # $1 = handle, prints the access token
  local body status
  body=$(jq -n --arg n "Race $1" --arg e "race$RUN.$1@$DOMAIN" --arg p "$PASSWORD" \
    '{name:$n, email:$e, password:$p}')
  status=$(curl -sS -o "$WORK/reg.json" -w '%{http_code}' -X POST "$API/api/auth/register" \
    -H 'Content-Type: application/json' -d "$body")
  if [ "$status" = "429" ]; then
    echo "Got 429 RATE_LIMITED while registering. Restart the backend with RATE_LIMIT_ENABLED=false." >&2
    exit 1
  fi
  [ "$status" = "201" ] || { echo "Register failed ($status): $(cat "$WORK/reg.json")" >&2; exit 1; }
  jq -r .accessToken "$WORK/reg.json"
}

tomorrow() { date -d tomorrow +%F 2>/dev/null || date -v+1d +%F; }

echo "Registering 1 creator + $JOINERS joiners ..."
CREATOR=$(register creator)
for i in $(seq 1 "$JOINERS"); do
  register "s$i" > "$WORK/token.$i"
done

RIDE_BODY=$(jq -n --arg d "$(tomorrow)" --argjson s "$SEATS" '{
  sourceName:"IIT Bhubaneswar", sourceLatitude:20.1484, sourceLongitude:85.6706,
  destinationName:"Airport", destinationLatitude:20.2444, destinationLongitude:85.8178,
  departureDate:$d, departureTime:"09:30", totalSeats:$s, totalFare:"600.00"}')
RIDE_ID=$(curl -fsS -X POST "$API/api/rides" -H "Authorization: Bearer $CREATOR" \
  -H 'Content-Type: application/json' -d "$RIDE_BODY" | jq -r '.ride.id')
FREE=$((SEATS - 1))
echo "Ride $RIDE_ID created: $SEATS seats, $FREE free. Firing $JOINERS joins in parallel ..."

START=$(date +%s)
for i in $(seq 1 "$JOINERS"); do
  (
    curl -sS -o "$WORK/join.$i.json" -w '%{http_code}' -X POST "$API/api/rides/$RIDE_ID/join" \
      -H "Authorization: Bearer $(cat "$WORK/token.$i")" -H 'Content-Type: application/json' \
      -d '{"seats":1}' > "$WORK/status.$i"
  ) &
done
wait
END=$(date +%s)

JOINED=0; FULL=0; OTHER=0
for i in $(seq 1 "$JOINERS"); do
  code=$(cat "$WORK/status.$i")
  if [ "$code" = "200" ]; then
    JOINED=$((JOINED + 1))
  elif [ "$code" = "409" ] && [ "$(jq -r .code "$WORK/join.$i.json")" = "RIDE_FULL" ]; then
    FULL=$((FULL + 1))
  else
    OTHER=$((OTHER + 1))
    echo "  unexpected response $code: $(cat "$WORK/join.$i.json")"
  fi
done

RIDE=$(curl -fsS "$API/api/rides/$RIDE_ID" -H "Authorization: Bearer $CREATOR")
OCCUPIED=$(echo "$RIDE" | jq -r .ride.occupiedSeats)
TOTAL=$(echo "$RIDE" | jq -r .ride.totalSeats)
MEMBERS=$(echo "$RIDE" | jq '[.participants[]?] | length')

echo
echo "Joins sent:          $JOINERS (in parallel, took ~$((END - START))s wall clock)"
echo "Joined (200):        $JOINED"
echo "Rejected RIDE_FULL:  $FULL"
echo "Other responses:     $OTHER"
echo "Seats occupied:      $OCCUPIED / $TOTAL   (participants listed: $MEMBERS)"
echo
if [ "$OCCUPIED" -le "$TOTAL" ] && [ "$JOINED" -eq "$FREE" ] && [ "$OTHER" -eq 0 ]; then
  echo "PASS: no over-booking, exactly $FREE of $JOINERS got a seat."
else
  echo "FAIL: expected exactly $FREE joins and occupied <= total."
  exit 1
fi
