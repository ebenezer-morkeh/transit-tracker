# 04: All five Triangle agencies + filters + fault tolerance

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** The service polls GoRaleigh, GoDurham, GoTriangle, Chapel Hill Transit, and GoCary in parallel from a config list (agency ID, vehicle positions URL, optional trip updates URL; URLs in the spec). API consumers can filter with `GET /api/vehicles?agency={id}&route={id}`. One failing, slow, or garbage feed is logged and skipped without affecting the others. Vehicles absent from an agency's latest successful poll disappear from results.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] All five agencies configured; adding an agency requires only a config entry
- [ ] Feeds fetched in parallel with a per-request timeout
- [ ] `agency` and `route` filters work; route without agency returns 400; unknown agency returns `[]`
- [ ] End-to-end test: two stubbed agencies sharing a route ID don't collide
- [ ] End-to-end test: one stubbed feed returns HTTP 500 / garbage bytes and the other agency's data is still saved and served
- [ ] End-to-end test: a vehicle missing from the next poll disappears from results
- [ ] Logs show per-agency poll outcome (count saved or failure reason)
- [ ] Running locally against live feeds shows vehicles from all five agencies
