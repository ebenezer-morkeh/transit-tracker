# 02: GoRaleigh vehicle positions end to end

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** An API consumer can call `GET /api/vehicles` and see GoRaleigh's vehicles (latest state) as JSON, after the service polls GoRaleigh's GTFS-realtime vehicle positions feed. Covers the feed parser (pure function: bytes + agency ID → vehicle positions), the `vehicle_position` table keyed by (agency ID, vehicle ID), a scheduled poller (~15s, configurable) with a "poll once now" method, and the read endpoint. Establishes both test seams from the spec.

**Blocked by:** 01

**Status:** done

- [x] A real GoRaleigh vehicle positions `.pb` payload is captured and committed as a test fixture
- [x] Parser unit tests on the fixture, plus hand-built edge cases (vehicle with no position is skipped, missing route/trip is allowed, empty feed returns empty)
- [x] Flyway migration creates `vehicle_position` with the spec's columns, primary key, and (agency, route) index
- [x] Polling updates rows in place; repeated polls don't duplicate
- [x] Scheduling is disabled in the test profile; tests call "poll once" directly
- [x] End-to-end test: JDK built-in stub HTTP server serves the fixture, Testcontainers Postgres, one poll, then `GET /api/vehicles` returns the fixture's vehicles with lat/lon/bearing/speed/trip/route/timestamp
- [x] Running the app locally against the live GoRaleigh feed shows real vehicles at `GET /api/vehicles`
