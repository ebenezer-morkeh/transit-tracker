CREATE TABLE vehicle_position (
    agency_id      TEXT             NOT NULL,
    vehicle_id     TEXT             NOT NULL,
    trip_id        TEXT,
    route_id       TEXT,
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    bearing        REAL,
    speed          REAL,
    feed_timestamp TIMESTAMPTZ      NOT NULL,
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (agency_id, vehicle_id)
);

CREATE INDEX vehicle_position_agency_route_idx ON vehicle_position (agency_id, route_id);
