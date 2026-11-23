# Agency feeds: quirks

The feed URLs live in `src/main/resources/application.properties` (`transit.feeds[n]`). This file holds what the URLs don't tell you. Facts were measured on 2026-11-22; re-measure before relying on a number.

## Every feed

- **Redirects.** Most feed and static-zip URLs redirect. Every `HttpClient` comes from `FeedHttp` (follows redirects, enforced by `ArchitectureTests`). Java's `Redirect.NORMAL` refuses https→http, so configure the final https URL when a host downgrades.
- **Empty is normal.** Late at night a vehicles feed can be ~15 bytes with zero entities (GoDurham, GoCary). A successful empty poll removes that agency's vehicles; that is correct, not a bug.
- **Timezone.** All five agencies use `America/New_York`.
- **Trip IDs match.** Realtime `trip_id`s matched the static `trips.txt` 100% for all five agencies.

## Delay coverage

Stop time updates that set `delay` (all set `time`):

| Agency | `delay` set |
|---|---|
| godurham | 1549 / 1554 |
| gotriangle | 77 / 529 |
| gocary | 42 / 183 |
| goraleigh | 0 / 2074 |
| chapelhill | 0 / 30 |

Hence ADR-0003: delays fall back to predicted minus scheduled time from static `stop_times.txt`.

## Per agency

- **goraleigh**: `FeedEntity.id` is 1..N per response, so key vehicles on `vehicle.vehicle.id`. Largest static schedule (~226k stop times, 18 MB `stop_times.txt`, ~6 s to load).
- **chapelhill**: `http://` URLs 308-redirect to `https://`; config uses https. Vehicles often have no `route_id`.
- **gotriangle**: the static GTFS link on the GoDurham developer page 404s; the working one is `https://gotriangle.org/gtfs`. Its TLS chain ends at Sectigo Public Server Authentication Root R46, missing from JDK 21 GA (2023) truststores: PKIX errors outside Docker mean the local JDK is out of date.
- **gocary**: the static zip URL redirects https→http, which Java won't follow; config points at the final https URL.
- **ncstate** (not used): serves `.json`, not GTFS-realtime protobuf.

Source list: https://godurhamtransit.org/developer-resources (Creative Commons attribution).
