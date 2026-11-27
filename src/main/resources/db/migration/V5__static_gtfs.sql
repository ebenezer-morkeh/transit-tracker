-- Static GTFS routes, stops, trips and service calendars, replaced wholesale per agency together with
-- scheduled_stop_time on each load.
CREATE TABLE route (
    agency_id  TEXT NOT NULL,
    route_id   TEXT NOT NULL,
    short_name TEXT,
    long_name  TEXT,
    color      TEXT,
    text_color TEXT,
    PRIMARY KEY (agency_id, route_id)
);

CREATE TABLE stop (
    agency_id TEXT             NOT NULL,
    stop_id   TEXT             NOT NULL,
    name      TEXT             NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (agency_id, stop_id)
);

CREATE TABLE trip (
    agency_id    TEXT NOT NULL,
    trip_id      TEXT NOT NULL,
    route_id     TEXT NOT NULL,
    service_id   TEXT NOT NULL,
    direction_id SMALLINT,
    headsign     TEXT,
    PRIMARY KEY (agency_id, trip_id)
);

CREATE INDEX trip_agency_route_idx ON trip (agency_id, route_id);

CREATE TABLE service_calendar (
    agency_id  TEXT    NOT NULL,
    service_id TEXT    NOT NULL,
    monday     BOOLEAN NOT NULL,
    tuesday    BOOLEAN NOT NULL,
    wednesday  BOOLEAN NOT NULL,
    thursday   BOOLEAN NOT NULL,
    friday     BOOLEAN NOT NULL,
    saturday   BOOLEAN NOT NULL,
    sunday     BOOLEAN NOT NULL,
    start_date DATE    NOT NULL,
    end_date   DATE    NOT NULL,
    PRIMARY KEY (agency_id, service_id)
);

-- added: true for GTFS exception_type 1 (service added on date), false for 2 (removed).
CREATE TABLE service_calendar_date (
    agency_id  TEXT    NOT NULL,
    service_id TEXT    NOT NULL,
    date       DATE    NOT NULL,
    added      BOOLEAN NOT NULL,
    PRIMARY KEY (agency_id, service_id, date)
);

CREATE INDEX scheduled_stop_time_agency_stop_idx ON scheduled_stop_time (agency_id, stop_id);
