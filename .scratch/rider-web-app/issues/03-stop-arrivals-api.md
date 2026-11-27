# 03: Stop arrivals API with feed status

Spec: `.scratch/rider-web-app/spec.md` (Arrivals, Feed status, API); ADR-0007

**What to build:** `GET /api/agencies/{agency}/stops/{stop}/arrivals` returns the stop's next 60 minutes of arrivals, up to 3 per route, each live (scheduled time + trip delay no older than 2 minutes) or scheduled. The poller records each agency's last successful poll, and the response flags when that agency's live data is unavailable.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Trips chosen by service day from `calendar.txt` + `calendar_dates.txt`, including yesterday's service day for times past 24:00:00
- [ ] Live vs scheduled per the 2-minute rule; past arrivals dropped; 60-minute window; 3 per route; routes ordered by first arrival
- [ ] Poller keeps last successful poll time per agency; "live data unavailable" when older than 2 minutes or none
- [ ] Unknown agency or stop → 404
- [ ] End-to-end tests for every rule above, plus a `calendar_dates` exception and a failed agency feed setting the flag (use a fixed clock)
- [ ] Endpoint appears in Swagger UI
