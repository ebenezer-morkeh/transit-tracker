-- Static GTFS stop_times, replaced wholesale per agency on each load. Times are seconds after local midnight of the
-- service day (may exceed 86400); null when the feed leaves a stop untimed.
CREATE TABLE scheduled_stop_time (
    agency_id         TEXT    NOT NULL,
    trip_id           TEXT    NOT NULL,
    stop_sequence     INTEGER NOT NULL,
    stop_id           TEXT    NOT NULL,
    arrival_seconds   INTEGER,
    departure_seconds INTEGER,
    PRIMARY KEY (agency_id, trip_id, stop_sequence)
);
