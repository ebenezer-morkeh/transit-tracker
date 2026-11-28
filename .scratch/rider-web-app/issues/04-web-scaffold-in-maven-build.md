# 04: Web app scaffold built and served by Spring Boot

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** A React + TypeScript + Vite app in `web/` that Maven builds into Spring Boot's static resources, served at `/` from the same Docker image. Vitest runs as part of `./mvnw verify`, so CI covers it with no workflow change.

**Blocked by:** none

**Status:** done

- [x] `web/` with React, TypeScript, Vite, react-leaflet, Vitest; Vite dev server proxies `/api` to `localhost:8080`
- [x] Maven installs its own Node and runs install, test and build; output lands in the jar's static resources
- [x] `/` serves the app; `/api/**`, `/actuator/**` and Swagger UI still work
- [x] Dockerfile still builds; `docker compose up` serves the app at `http://localhost:8080/`
- [x] `web/node_modules` and build output git-ignored and excluded from the Docker build context

## Notes (2026-11-27)

- `frontend-maven-plugin` 1.15.1 with Node v22.12.0; `npm run build` runs `tsc --noEmit` first. Vitest runs in the `test` phase and honours `-DskipTests`.
- Vite builds into `target/classes/static`; `/` forwards to `index.html` (Spring Boot welcome page).
- Docker build verified: the image builds and serves the app.
