# 04: Web app scaffold built and served by Spring Boot

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** A React + TypeScript + Vite app in `web/` that Maven builds into Spring Boot's static resources, served at `/` from the same Docker image. Vitest runs as part of `./mvnw verify`, so CI covers it with no workflow change.

**Blocked by:** none

**Status:** ready-for-agent

- [ ] `web/` with React, TypeScript, Vite, react-leaflet, Vitest; Vite dev server proxies `/api` to `localhost:8080`
- [ ] Maven installs its own Node and runs install, test and build; output lands in the jar's static resources
- [ ] `/` serves the app; `/api/**`, `/actuator/**` and Swagger UI still work
- [ ] Dockerfile still builds; `docker compose up` serves the app at `http://localhost:8080/`
- [ ] `web/node_modules` and build output git-ignored and excluded from the Docker build context
