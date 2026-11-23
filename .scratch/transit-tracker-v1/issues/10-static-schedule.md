# 10: Static schedule (stop_times) loader

Spec: `.scratch/transit-tracker-v1/spec.md` (Static schedule section)

**What to build:** On startup and once a day, the service downloads each agency's static GTFS zip and stores its scheduled stop times, so trip delays can be calculated for agencies whose realtime feeds send only predicted times (GoRaleigh, Chapel Hill). Each agency's schedule is replaced wholesale in one transaction. A failed download or bad zip keeps the previous schedule and is logged, never fatal.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Feed config entries gain a static GTFS URL (the five URLs in the spec); HTTP client follows redirects
- [ ] Flyway migration creates `scheduled_stop_time` per spec (PK agency, trip, stop sequence)
- [ ] Streaming parse of `stop_times.txt` (handles quoted CSV, BOM, CRLF, header column order, times past 24:00:00) into batched inserts
- [ ] Per-agency replace in a single transaction; failed load keeps the old rows
- [ ] Loader runs on startup and on a daily schedule; disabled in the test profile, with a "load now" method tests can call
- [ ] Unit test: small hand-built `stop_times.txt` covering quirks above
- [ ] End-to-end test: stub server serves a tiny fixture zip; after load, rows exist; a broken zip for one agency leaves the other agency's (and its own previous) rows intact
- [ ] Live check: all five agencies load; log row counts and load time (GoRaleigh is ~18 MB, should load in seconds)
