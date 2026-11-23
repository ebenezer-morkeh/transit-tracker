# 06: Trip delays per route

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** API consumers can call `GET /api/agencies/{agency}/routes/{route}/delays` and get the current delay (seconds, positive is late, negative is early) for each active trip on that route, most delayed first. The poller also fetches each agency's trip updates feed when configured; agencies without one still contribute vehicle positions. Uses a `trip_delay` table keyed by (agency ID, trip ID), latest state only.

**Blocked by:** 04, 10

**Status:** done

- [x] A real GoRaleigh trip updates `.pb` payload (predicted times only, no `delay`) is captured and committed as a test fixture, plus a small fixture where `delay` is set
- [x] Delay rule per spec: next upcoming stop time update; use its `delay` if set, else predicted time − scheduled time from `scheduled_stop_time` (match trip + stop sequence, else stop ID; service date from `start_date` else today America/New_York); fall back to trip-level delay; skip trips with neither
- [x] Parser unit tests on the fixture plus edge cases (no stop time updates, missing route, empty feed)
- [x] Flyway migration creates `trip_delay` with the spec's columns, primary key, and (agency, route) index
- [x] Trips absent from the latest successful poll are removed
- [x] End-to-end test: delays endpoint returns stubbed trips sorted most delayed first; empty route returns `[]`
- [x] Running locally against live feeds returns real delays for GoRaleigh (computed) and GoDurham (feed-provided)
