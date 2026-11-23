# Glossary

**Agency**: one transit operator, identified by a lowercase slug (`goraleigh`, `godurham`, `gotriangle`, `chapelhill`, `gocary`). Every stored row and every route ID is scoped to an agency.
_Avoid_: operator, system, provider.

**Feed**: one agency's configured set of URLs: vehicle positions and trip updates (GTFS-realtime protobuf, polled every 15s) and a static GTFS zip (loaded daily). See `docs/feeds.md`.
_Avoid_: source, endpoint.

**Poll**: one cycle of fetching every agency's realtime feeds in parallel. A poll succeeds or fails per agency.

**Vehicle position**: the latest reported location of one vehicle (agency + vehicle ID), with optional trip, route, bearing and speed. Replaced on every successful poll; vehicles missing from it are removed.
_Avoid_: bus, location, ping.

**Trip prediction**: what the parser extracts from a trip update: the trip's next upcoming stop event (predicted time and/or feed delay) plus its trip-level delay. Not stored.

**Trip delay**: how late one active trip is running, in seconds (positive late, negative early), resolved from a trip prediction by the delay rule (ADR-0003). Stored, latest only.
_Avoid_: lateness, lag.

**Scheduled stop time**: one row of an agency's static `stop_times.txt`: the planned arrival/departure, as seconds after the service day's reference time, of a trip at a stop sequence.

**Service day**: the day a trip is scheduled on, from the trip's `start_date`, else today in America/New_York. Its reference time is noon minus 12h, so GTFS times past 24:00:00 and DST change days work.
_Avoid_: calendar day, date.

**Latest state**: the storage model: only the newest vehicle positions and trip delays exist, never history (ADR-0002).
