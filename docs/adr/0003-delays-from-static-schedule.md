# 0003: Compute delays from the static schedule when the feed has none

Status: accepted (2026-11-22). Supersedes the spec's original "skip static GTFS in v1".

GoRaleigh and Chapel Hill trip updates set predicted `time` but never `delay` (coverage table in `docs/feeds.md`). Without a schedule, the delays endpoint would be empty for the largest agency.

Rule: use the next upcoming stop's feed `delay` if set; else predicted time minus the scheduled stop time from static `stop_times.txt` (matched on trip + stop sequence, else stop ID; service day reference noon minus 12h); else the trip-level delay; else skip the trip.

Consequences: the service downloads five static zips at startup and daily (~500k rows, ~17 s). A failed or empty load keeps the previous schedule. Rejected alternative: report predicted arrival times instead of delays, which would make "N min late" impossible for two of five agencies.
