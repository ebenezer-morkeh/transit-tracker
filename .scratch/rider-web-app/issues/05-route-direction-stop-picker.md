# 05: Route, direction and stop picker with bookmarkable links

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** A mobile-first flow: a searchable list of all routes (short name, long name, agency tag), direction tabs labelled by headsign, and the direction's ordered stop list. The selection lives in `?agency=&route=&direction=&stop=` so any state can be bookmarked or shared.

**Blocked by:** 02, 04

**Status:** done

- [x] Route list with text search; route colour shown only alongside its text
- [x] Direction tabs and ordered stop list from the route stops endpoint
- [x] Query parameters read on load and written on every selection; back button works
- [x] Vitest: query parameter parsing and writing
- [x] WCAG 2.2 AA: keyboard usable, visible focus, labelled controls, AA contrast

## Notes (2026-11-27)

- Direction choice is a group of toggle buttons (`aria-pressed`) rather than ARIA tabs: simpler and fully keyboard-usable.
- Chapel Hill long names repeat the short name ("A - …"); `routeName` strips that prefix (Vitest).
- 2026-11-27: on request, the searchable route list became one native dropdown per agency (no search box).
