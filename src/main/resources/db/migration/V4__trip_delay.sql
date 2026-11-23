CREATE TABLE trip_delay (
    agency_id      TEXT        NOT NULL,
    trip_id        TEXT        NOT NULL,
    route_id       TEXT        NOT NULL,
    vehicle_id     TEXT,
    delay_seconds  INTEGER     NOT NULL,
    feed_timestamp TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (agency_id, trip_id)
);

CREATE INDEX trip_delay_agency_route_idx ON trip_delay (agency_id, route_id);
