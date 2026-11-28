# 02: Routes and route stops API

Spec: `.scratch/rider-web-app/spec.md` (Directions, API)

**What to build:** `GET /api/routes` lists every route across agencies, and `GET /api/agencies/{agency}/routes/{route}/stops` returns the route's directions (ID, most common headsign) each with its stops in the order of that direction's trip with the most stops.

**Blocked by:** 01

**Status:** done

- [x] `GET /api/routes` sorted by agency, then short name in natural order (2 before 10)
- [x] Route stops grouped by direction; a route with no `direction_id` has one direction
- [x] Unknown agency or route → 404; invalid params → 400
- [x] End-to-end tests: sorting, two agencies sharing a route ID, directions and headsigns, stop order
- [x] Endpoints appear in Swagger UI

## Notes (2026-11-27)

- `RouteService` sorts by agency, then short name (route ID if none) with a natural comparator; the SQL picks each direction's longest trip and most common headsign.
- No 400 case: both inputs are path segments, so there's nothing to be blank or malformed.
