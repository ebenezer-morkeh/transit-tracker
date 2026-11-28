# 0007: Arrivals are scheduled time plus the trip's current delay

Status: accepted (2026-11-23)

A live arrival at any later stop is that stop's scheduled time plus the trip's one current trip delay (ADR-0003), applied unchanged to every remaining stop. A trip with no trip delay, or one older than 2 minutes, gives a scheduled arrival instead.

Why: it reuses the one stored delay per trip. Using the feed's per-stop predicted times would mean changing the parser to keep every stop time update and storing one row per trip per stop, and GoRaleigh and Chapel Hill only send predicted times, so they would still need the schedule to be converted.

Consequences: arrivals far down a route don't reflect a bus catching up or falling further behind. Rejected alternative: store per-stop predictions from trip updates.
