# 06: Trip delays per route

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** API consumers can call `GET /api/agencies/{agency}/routes/{route}/delays` and get the current delay (seconds, positive is late, negative is early) for each active trip on that route, most delayed first. The poller also fetches each agency's trip updates feed when configured; agencies without one still contribute vehicle positions. Uses a `trip_delay` table keyed by (agency ID, trip ID), latest state only.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] A real trip updates `.pb` payload is captured and committed as a test fixture
- [ ] Delay rule per spec: next upcoming stop time update (arrival delay, else departure delay), falling back to trip-level delay; trips with no delay info skipped
- [ ] Parser unit tests on the fixture plus edge cases (no stop time updates, missing route, empty feed)
- [ ] Flyway migration creates `trip_delay` with the spec's columns, primary key, and (agency, route) index
- [ ] Trips absent from the latest successful poll are removed
- [ ] End-to-end test: delays endpoint returns stubbed trips sorted most delayed first; empty route returns `[]`
- [ ] Running locally against live feeds returns real delays for at least one route
