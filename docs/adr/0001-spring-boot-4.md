# 0001: Stay on Spring Boot 4.0.x

Status: accepted (2026-11-22)

We use Spring Boot 4.0.10 on Java 21, not the 3.5.x fallback. Boot 4.0.x works with everything v1 needs:

- Flyway, through `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
- Testcontainers 2.x (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) with `@ServiceConnection`.
- `org.mobilitydata:gtfs-realtime-bindings:0.2.0`.

`TransitTrackerApplicationTests` checks all three: health is UP against real Postgres, the V1 migration is applied, and a GTFS-rt `FeedMessage` round-trips.

We picked 4.0.x over the newer 4.1/4.2 lines because the spec pins 4.0.x.
