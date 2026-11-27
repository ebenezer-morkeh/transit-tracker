# 01: Load routes, stops, trips and calendars from static GTFS

Spec: `.scratch/rider-web-app/spec.md` (Static GTFS)

**What to build:** The daily static loader also stores each agency's `routes.txt`, `stops.txt`, `trips.txt`, `calendar.txt` and `calendar_dates.txt`, replaced wholesale per agency in the same transaction as `stop_times.txt`. A failed or broken load keeps all of that agency's previous data.

**Blocked by:** none

**Status:** done

- [x] Flyway migration for `route`, `stop`, `trip`, `service_calendar`, `service_calendar_date` per spec, plus an index on `scheduled_stop_time (agency_id, stop_id)`
- [x] Streaming parse of each file with the same CSV handling as `stop_times.txt` (quotes, BOM, CRLF, header order); missing calendar files treated as empty
- [x] All six files replaced per agency in one transaction; broken zip keeps the previous rows for every table
- [x] End-to-end test: fixture zip loads into all tables; a broken zip leaves previous rows intact
- [x] Live check: all five agencies load; record row counts and total load time against the ~17 s v1 baseline; add any per-agency quirks to `docs/feeds.md`

## Notes (2026-11-27)

- Read API for later issues on `ScheduleService`: `routes()`, `stop(agency, stopId)`, `trip(agency, tripId)`, `servicesOn(agency, date)`.
- Stops with location type 3 or 4 (generic nodes, boarding areas) are skipped: riders don't wait there.
- An empty routes, stops, trips or stop_times file, or both calendar files empty, is a failed load that keeps the previous data. `DelaysApiTests` now builds its zip with `StaticScheduleTests.gtfsZip`, which adds minimal valid files.
- Live run in Docker: all five agencies loaded in ~23 s total (v1: ~17 s). Counts and coverage in `docs/feeds.md`; no new quirks.
