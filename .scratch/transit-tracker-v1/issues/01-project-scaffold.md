# 01: Project scaffold + Spring Boot 4 compatibility check

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** A Maven project (with wrapper) on Java 21 and Spring Boot 4.0.x that starts against Postgres and reports healthy. Flyway, the Postgres driver, Testcontainers, and the official `gtfs-realtime-bindings` library are on the classpath and confirmed working together. If any of them is incompatible with Boot 4, fall back to the latest Boot 3.5.x and record why in the README notes or an ADR.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `./mvnw verify` passes locally
- [x] App boots against Postgres and `/actuator/health` returns UP
- [x] Flyway runs a baseline migration on startup
- [x] One Testcontainers integration test boots the full app against real Postgres and asserts health is UP
- [x] Boot version decision recorded (4.0.x confirmed, or fallback reason documented)
- [x] MIT `LICENSE` file and a `.gitignore` for Maven/IDE files
