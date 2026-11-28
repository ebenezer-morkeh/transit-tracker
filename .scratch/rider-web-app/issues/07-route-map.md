# 07: Route map with live vehicles and stops

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** A map (react-leaflet, OSM tiles with attribution) showing the selected route's stops and its live vehicles from `GET /api/vehicles/stream?agency=&route=`. Tapping a stop selects it, the same as choosing it from the list.

**Blocked by:** 05

**Status:** done

- [x] Stops of the selected direction as markers; map fits them on route change
- [x] Vehicles from the SSE stream, rotated by bearing when present; stream reconnects on route change and closes on unmount
- [x] Tapping a stop updates the selection and query parameters
- [x] Notice when there are no vehicles and the agency's live data is unavailable
- [x] The map is supplementary: everything it offers is also reachable from the lists
- [x] Manual check on a phone-sized viewport against live feeds

## Notes (2026-11-27)

- Deviation: the map's empty notice says "No live vehicles on this route right now" without checking the agency's feed status (that flag only comes with a stop's arrivals). The arrivals panel still shows the feed notice.
- On phones the map sits between the arrivals and the stop list (CSS order); a `ResizeObserver` redraws it on rotation/layout change.
- Checked by hand: stops and live vehicles draw, bearing arrows rotate, tapping a stop selects it and updates the link.
