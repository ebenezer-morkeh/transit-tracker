# 02: Routes and route stops API

Spec: `.scratch/rider-web-app/spec.md` (Directions, API)

**What to build:** `GET /api/routes` lists every route across agencies, and `GET /api/agencies/{agency}/routes/{route}/stops` returns the route's directions (ID, most common headsign) each with its stops in the order of that direction's trip with the most stops.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] `GET /api/routes` sorted by agency, then short name in natural order (2 before 10)
- [ ] Route stops grouped by direction; a route with no `direction_id` has one direction
- [ ] Unknown agency or route → 404; invalid params → 400
- [ ] End-to-end tests: sorting, two agencies sharing a route ID, directions and headsigns, stop order
- [ ] Endpoints appear in Swagger UI
