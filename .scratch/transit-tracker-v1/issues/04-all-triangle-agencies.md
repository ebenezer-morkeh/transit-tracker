# 04: All five Triangle agencies + filters + fault tolerance

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** The service polls GoRaleigh, GoDurham, GoTriangle, Chapel Hill Transit, and GoCary in parallel from a config list (agency ID, vehicle positions URL, optional trip updates URL; URLs in the spec). API consumers can filter with `GET /api/vehicles?agency={id}&route={id}`. One failing, slow, or garbage feed is logged and skipped without affecting the others. Vehicles absent from an agency's latest successful poll disappear from results.

**Blocked by:** 02

**Status:** done

- [x] All five agencies configured; adding an agency requires only a config entry
- [x] Feeds fetched in parallel with a per-request timeout
- [x] `agency` and `route` filters work; route without agency returns 400; unknown agency returns `[]`
- [x] End-to-end test: two stubbed agencies sharing a route ID don't collide
- [x] End-to-end test: one stubbed feed returns HTTP 500 / garbage bytes and the other agency's data is still saved and served
- [x] End-to-end test: a vehicle missing from the next poll disappears from results
- [x] Logs show per-agency poll outcome (count saved or failure reason)
- [x] Running locally against live feeds shows vehicles from all five agencies
