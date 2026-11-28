# 03: Stop arrivals API with feed status

Spec: `.scratch/rider-web-app/spec.md` (Arrivals, Feed status, API); ADR-0007

**What to build:** `GET /api/agencies/{agency}/stops/{stop}/arrivals` returns the stop's next 60 minutes of arrivals, up to 3 per route, each live (scheduled time + trip delay no older than 2 minutes) or scheduled. The poller records each agency's last successful poll, and the response flags when that agency's live data is unavailable.

**Blocked by:** 01

**Status:** done

- [x] Trips chosen by service day from `calendar.txt` + `calendar_dates.txt`, including yesterday's service day for times past 24:00:00
- [x] Live vs scheduled per the 2-minute rule; past arrivals dropped; 60-minute window; 3 per route; routes ordered by first arrival
- [x] Poller keeps last successful poll time per agency; "live data unavailable" when older than 2 minutes or none
- [x] Unknown agency or stop → 404
- [x] End-to-end tests for every rule above, plus a `calendar_dates` exception and a failed agency feed setting the flag (use a fixed clock)
- [x] Endpoint appears in Swagger UI

## Notes (2026-11-27)

- `FeedStatus` (ingest) records each agency's last successful trip updates poll, in memory. A `Clock` bean makes "now" fixable in tests.
- `ServiceDay.at` (schedule) now holds the noon-minus-12h rule; `TripDelays` uses it.
- Calls are fetched from 90 min before to 90 min after now (shortcut comment in `ArrivalService`), then filtered by expected time, so very late or early trips still appear.
- Stop times with neither arrival nor departure time get no arrivals (no interpolation).
