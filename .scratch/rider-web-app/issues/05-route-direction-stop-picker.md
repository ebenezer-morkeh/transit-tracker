# 05: Route, direction and stop picker with bookmarkable links

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** A mobile-first flow: a searchable list of all routes (short name, long name, agency tag), direction tabs labelled by headsign, and the direction's ordered stop list. The selection lives in `?agency=&route=&direction=&stop=` so any state can be bookmarked or shared.

**Blocked by:** 02, 04

**Status:** ready-for-agent

- [ ] Route list with text search; route colour shown only alongside its text
- [ ] Direction tabs and ordered stop list from the route stops endpoint
- [ ] Query parameters read on load and written on every selection; back button works
- [ ] Vitest: query parameter parsing and writing
- [ ] WCAG 2.2 AA: keyboard usable, visible focus, labelled controls, AA contrast
