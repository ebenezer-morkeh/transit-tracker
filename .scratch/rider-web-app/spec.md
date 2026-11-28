# Spec: Rider web app

Status: done (integration/transit-tracker-v1)

## Problem Statement

Riders in the Triangle want to know when their bus will reach their stop and where it is right now. The backend already tracks every vehicle and every running trip's delay across five agencies, but there's no screen a rider can use. It also only knows raw route IDs and stop IDs, and only knows about trips that are already running, so it can't answer "when is the next bus" for a bus that hasn't started its trip.

## Solution

A mobile-first web app served by the existing Spring Boot service. A rider picks a route (from one list covering all five agencies), a direction, and a stop, then sees that stop's next arrivals and the route's vehicles moving on a map. Arrivals are live when the trip has a current delay and scheduled otherwise. Every stop has its own link riders can bookmark.

The backend learns route names, stops, trip directions and service calendars from the static GTFS zips it already downloads, and gains endpoints for routes, a route's stops and a stop's arrivals.

It runs locally only (`docker compose up`) and keeps the $0 cost rule. Hosting it for the public is a separate, later decision.

## User Stories

1. As a rider, I want one list of every route across all five agencies, labelled with its short name, long name and agency, so that I find "7 – South Saunders" without knowing which agency runs it.
2. As a rider, I want to choose a route's direction by where it's heading, so that I see the stops on my side of the street.
3. As a rider, I want a route's stops listed in the order the bus visits them, so that I can find mine.
4. As a rider, I want to see the next 60 minutes of arrivals at my stop, up to 3 per route, so that I know when to leave.
5. As a rider, I want each arrival as "Due" or "N min" with the clock time underneath, so that I can read it at a glance.
6. As a rider, I want to know whether an arrival is live (and how late or early) or only scheduled, so that I know how far to trust it.
7. As a rider, I want buses that haven't started their trip yet to appear as scheduled arrivals, so that the list isn't empty when the next bus is 20 minutes away.
8. As a rider, I want arrivals that have already passed to disappear, so that I don't wait for a bus that has gone.
9. As a rider, I want arrivals to refresh on their own, so that I don't have to reload.
10. As a rider, I want a notice when an agency's live data is unavailable, so that I know the times are schedule only.
11. As a rider, I want the selected route's vehicles moving on a map along with its stops, so that I can see where my bus is.
12. As a rider, I want to tap a stop on the map to see its arrivals, so that I can pick a stop by location.
13. As a rider, I want a link to my stop that I can bookmark, so that I can check my commute in one tap.
14. As a rider on a phone, I want the layout to fit a small screen, so that I can use it at the stop.
15. As a rider using a screen reader or in bright sun, I want arrivals as a text list that meets WCAG 2.2 AA, so that I never depend on the map.
16. As the project owner, I want `./mvnw verify` to build and test the web app too, so that a broken UI fails CI.
17. As the project owner, I want the web app in the same Docker image, so that `docker compose up` still runs everything.

## Implementation Decisions

**Domain terms** (see `GLOSSARY.md`): Route, Stop, Arrival (live or scheduled) are new; Trip delay, Service day and Scheduled stop time are unchanged.

**Static GTFS**
- The existing daily loader also reads `routes.txt`, `stops.txt`, `trips.txt`, `calendar.txt` and `calendar_dates.txt` from each agency's zip. Each file is replaced wholesale per agency in the same transaction as `stop_times.txt`, so an agency's schedule is always consistent. A failed load keeps the previous data, as today. `shapes.txt` is not read.
- Either calendar file may be missing (GTFS allows a feed to use only one); treat a missing file as empty. Check each agency's zip for quirks and add them to `docs/feeds.md`.
- New tables (latest load only):
  - `route`: agency, route ID, short name, long name, colour, text colour (nullable). PK (agency, route ID).
  - `stop`: agency, stop ID, name, latitude, longitude. PK (agency, stop ID).
  - `trip`: agency, trip ID, route ID, service ID, direction ID (nullable), headsign (nullable). PK (agency, trip ID). Index (agency, route ID).
  - `service_calendar` and `service_calendar_date`, as in GTFS.
  - `scheduled_stop_time` needs an index on (agency, stop ID) for arrivals.
- Record the extra load time in issue notes (v1 baseline: ~17 s for all five agencies).

**Directions**
- A route has up to two directions, from `trips.txt` `direction_id`. Each direction is labelled with its most common headsign. A route whose trips have no `direction_id` has one direction.
- A direction's stop order is the stop sequence of its trip with the most stops.

**Arrivals** (ADR-0007)
- For a stop, find trips that run on the relevant service day (from the calendar files) and call at that stop within the next 60 minutes. Consider yesterday's service day too, for trips past 24:00:00.
- A trip with a trip delay whose feed timestamp is at most 2 minutes old is **live**: expected time = scheduled stop time + delay. Otherwise it's **scheduled**: expected time = scheduled stop time.
- Drop arrivals whose expected time has passed. Group by route, keep the 3 soonest per route, routes ordered by their first arrival.
- Cancelled trips are not handled; they show as scheduled (known gap).

**Feed status**
- The poller records each agency's last successful poll time (in memory is fine; it resets on restart). An agency is "live data unavailable" when its last success is more than 2 minutes ago or there has been none.

**API** (all read-only, JSON, invalid params → 400, unknown agency/route/stop → 404, empty → `[]`)
- `GET /api/routes`: every route across agencies: agency, route ID, short name, long name, colour, text colour. Sorted by agency, then short name in natural order.
- `GET /api/agencies/{agency}/routes/{route}/stops`: the route's directions, each with its ID, headsign and ordered stops (stop ID, name, latitude, longitude).
- `GET /api/agencies/{agency}/stops/{stop}/arrivals`: the stop's name and location, whether the agency's live data is unavailable, and arrivals grouped by route: route ID, short name, colour, headsign, trip ID, expected time (ISO-8601 with offset), scheduled time, live flag, delay seconds (live only).
- Existing endpoints and the SSE stream are unchanged.

**Web app**
- React + TypeScript + Vite in `web/`, using react-leaflet with OpenStreetMap tiles (attribution shown, per OSM's tile policy).
- Maven builds it during `package` (for example `frontend-maven-plugin`, which installs its own Node so no system Node is needed) and copies the output into Spring Boot's static resources. Served at `/`, same origin as the API, no CORS. Vite's dev server proxies `/api` to `localhost:8080` for local development.
- Flow: route list (searchable, agency tag per route) → direction tabs → ordered stop list. Selecting a stop opens its arrivals. State lives in query parameters: `?agency=&route=&direction=&stop=`; opening a link with `agency` and `stop` goes straight to arrivals.
- Arrivals panel re-fetches every 15 s. Shows "Due" (under 1 minute) or "N min", the clock time in America/New_York, and "live · 3 min late" / "live · on time" / "live · 2 min early" or "scheduled". Shows "Live times unavailable for {agency}, showing schedule" when flagged.
- Map shows the selected route's stops and its live vehicles, from `GET /api/vehicles/stream?agency=&route=`. Tapping a stop selects it. With no vehicles and the agency flagged, the map shows the same notice.
- Mobile-first layout. WCAG 2.2 AA: arrivals and stops are real text lists usable without the map, with visible focus, labelled controls and AA colour contrast. Route colours from GTFS are used only alongside text, never as the only signal.

## Testing Decisions

- **Good tests** check behaviour visible from outside: what an endpoint returns for given feed and schedule fixtures, or what a display function returns for given input. No assertions on SQL, internal classes or mocked collaborators.
- **Backend (main seam):** Spring Boot + Testcontainers tests in the existing style. The stub server serves a tiny fixture GTFS zip with routes, stops, trips and calendars, plus realtime fixtures. Cover:
  - routes list and sorting; two agencies with the same route ID don't collide
  - a route's directions, headsigns and stop order
  - a stop's arrivals: live vs scheduled, the 2-minute staleness cut-off, past arrivals hidden, the 60-minute window, 3 per route, a trip not running on today's calendar excluded, a `calendar_dates` exception applied, a trip past 24:00:00 from yesterday's service day
  - the "live data unavailable" flag when an agency's feed fails
  - a broken zip keeps the previous routes, stops, trips and calendars
- **Frontend:** Vitest unit tests for the pure display logic: "Due" / "N min", clock formatting in America/New_York, late/early/on-time wording, and reading and writing the query parameters.
- **Not tested automatically:** map rendering and the full UI flow (no Playwright for now). Check them by hand with `docker compose up` against live feeds, on a phone-sized viewport.
- **Prior art:** `src/test` end-to-end tests with the JDK stub server and fixture zips (issue 10 in v1).

## Out of Scope

- Public hosting: it runs locally only, and deploying is a later decision.
- Cancelled trips and other trip-level schedule changes from realtime feeds.
- Finding stops by location ("stops near me").
- Route shapes from `shapes.txt`.
- Playwright or other browser tests.
- Native apps and PWA features (install prompts, offline, push).
- Service alerts, trip planning, accounts.

## Further Notes

- OSM tile usage is fine at local/demo traffic. Public hosting would need a tile provider with its own terms.
- README's "what's next" should drop the map item and add: public hosting, cancellations, stops near me, route shapes.
