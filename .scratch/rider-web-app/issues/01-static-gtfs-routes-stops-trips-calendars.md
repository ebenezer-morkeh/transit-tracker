# 01: Load routes, stops, trips and calendars from static GTFS

Spec: `.scratch/rider-web-app/spec.md` (Static GTFS)

**What to build:** The daily static loader also stores each agency's `routes.txt`, `stops.txt`, `trips.txt`, `calendar.txt` and `calendar_dates.txt`, replaced wholesale per agency in the same transaction as `stop_times.txt`. A failed or broken load keeps all of that agency's previous data.

**Blocked by:** none

**Status:** ready-for-agent

- [ ] Flyway migration for `route`, `stop`, `trip`, `service_calendar`, `service_calendar_date` per spec, plus an index on `scheduled_stop_time (agency_id, stop_id)`
- [ ] Streaming parse of each file with the same CSV handling as `stop_times.txt` (quotes, BOM, CRLF, header order); missing calendar files treated as empty
- [ ] All six files replaced per agency in one transaction; broken zip keeps the previous rows for every table
- [ ] End-to-end test: fixture zip loads into all tables; a broken zip leaves previous rows intact
- [ ] Live check: all five agencies load; record row counts and total load time against the ~17 s v1 baseline; add any per-agency quirks to `docs/feeds.md`
