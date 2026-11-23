# 05: Docker + docker compose local stack

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** Anyone can run `docker compose up` and get the app plus Postgres serving live Triangle vehicle data at `GET /api/vehicles`. Multi-stage Dockerfile (Maven build stage, slim Java 21 JRE runtime stage). Compose waits for Postgres to be healthy before starting the app.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] Multi-stage Dockerfile builds the app image
- [ ] `docker-compose.yml` with app + Postgres, Postgres healthcheck, app depends on healthy DB
- [ ] DB credentials come from compose environment, not hardcoded in the image
- [ ] `docker compose up` → `/actuator/health` UP and `GET /api/vehicles` returns live data
- [ ] App can still be run from the IDE against the compose Postgres
