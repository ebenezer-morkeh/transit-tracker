# 07: Route map with live vehicles and stops

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** A map (react-leaflet, OSM tiles with attribution) showing the selected route's stops and its live vehicles from `GET /api/vehicles/stream?agency=&route=`. Tapping a stop selects it, the same as choosing it from the list.

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] Stops of the selected direction as markers; map fits them on route change
- [ ] Vehicles from the SSE stream, rotated by bearing when present; stream reconnects on route change and closes on unmount
- [ ] Tapping a stop updates the selection and query parameters
- [ ] Notice when there are no vehicles and the agency's live data is unavailable
- [ ] The map is supplementary: everything it offers is also reachable from the lists
- [ ] Manual check on a phone-sized viewport against live feeds
