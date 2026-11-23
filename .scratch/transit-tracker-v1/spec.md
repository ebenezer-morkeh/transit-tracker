# Spec: Transit Tracker v1 (Triangle region, backend only)

Status: ready-for-agent

## Problem Statement

Riders and developers in the Raleigh–Durham–Chapel Hill area have no single place to see where every bus is right now and how late it's running. Each agency (GoRaleigh, GoDurham, GoTriangle, Chapel Hill Transit, GoCary) publishes its own GTFS-realtime feed from a different vendor, in raw protobuf, with overlapping route IDs. Nobody can query "where are the route 1 buses and how late are they" across agencies without writing their own feed plumbing.

The project owner also wants a resume portfolio project that shows production-style backend skills (Java/Spring, Postgres, testing, Docker, CI, infrastructure as code) and costs $0 to build and run.

## Solution

A Spring Boot service that polls all five Triangle agencies' GTFS-realtime feeds every ~15 seconds. It normalizes them into one agency-aware model of **vehicle positions** and **trip delays**, keeps only the latest state in Postgres, and serves it through a small REST API plus a live Server-Sent Events stream.

`docker compose up` runs the whole thing locally against live data. CI builds and tests every change. AWS infrastructure (ECS Fargate, RDS, ECR, CloudWatch) and a GitHub OIDC deploy workflow are written and validated but never applied, so the project costs nothing until the owner chooses to deploy.

## User Stories

1. As an API consumer, I want to list all vehicles currently in service across all Triangle agencies, so that I can see the whole region at once.
2. As an API consumer, I want to filter vehicles by agency, so that I can look at one system, like GoRaleigh, on its own.
3. As an API consumer, I want to filter vehicles by agency and route, so that I can see only the buses on the route I care about.
4. As an API consumer, I want each vehicle to include its latitude, longitude, bearing, and speed when the feed provides them, so that I can place and orient it on a map.
5. As an API consumer, I want each vehicle to include its trip ID and route ID, so that I can tie it to a delay and a route.
6. As an API consumer, I want each vehicle to include the feed timestamp of its last reported position, so that I can tell fresh positions from stale ones.
7. As an API consumer, I want vehicles that have stopped reporting to drop out of the results, so that I don't show ghost buses.
8. As an API consumer, I want to get the current delays for a route within an agency, so that I can tell riders how late that route is running.
9. As an API consumer, I want delays expressed in seconds (positive is late, negative is early), so that I can format them however I like.
10. As an API consumer, I want delays sorted with the most delayed trip first, so that the worst problems are obvious.
11. As an API consumer, I want GoRaleigh route 1 and GoDurham route 1 to never be confused, so that data from different agencies doesn't mix.
12. As an API consumer, I want to subscribe to a live stream of vehicle updates, optionally filtered by agency and route, so that a map can move without polling.
13. As an API consumer, I want the stream to push right after each successful poll, so that updates arrive as soon as the backend has them.
14. As an API consumer, I want an empty list (not an error) when a route has no active vehicles, so that my client handles quiet routes simply.
15. As an API consumer, I want a clear 400 response for invalid query parameters, and an empty result for an unknown agency, so that mistakes are easy to diagnose.
16. As an API consumer, I want JSON responses with stable, documented field names, so that I can build a frontend against them.
17. As a developer, I want to add a new agency by adding a config entry (agency ID, vehicle positions URL, trip updates URL), so that expanding coverage needs no code changes.
18. As a developer, I want an agency without a trip updates feed to still contribute vehicle positions, so that partial feeds are useful.
19. As an operator, I want one failing or slow feed to be logged and skipped without blocking the others, so that one agency's outage doesn't take down the region.
20. As an operator, I want each feed fetch to have a timeout, so that a hung feed can't stall the poller.
21. As an operator, I want feeds polled in parallel, so that a poll cycle stays well under the polling interval.
22. As an operator, I want the polling interval to be configurable, so that I can tune load and freshness.
23. As an operator, I want malformed or empty protobuf payloads handled without crashing the poller, so that bad vendor data is survivable.
24. As an operator, I want feed entities missing required fields (such as a vehicle with no position) skipped rather than failing the whole feed, so that one bad record doesn't discard good ones.
25. As an operator, I want a health endpoint, so that Docker and ECS can tell whether the service is up.
26. As an operator, I want structured logs saying which agency was polled, how many entities were saved, and any failures, so that I can see what the poller is doing.
27. As an operator, I want the database schema created and changed through versioned migrations, so that every environment has the same schema.
28. As an operator, I want only the latest state stored (updated in place per vehicle and per trip), so that the database stays small with no cleanup job.
29. As a developer, I want `docker compose up` to start the app and Postgres and begin serving live data, so that anyone can run the project with one command.
30. As a developer, I want to run the app from my IDE against the compose Postgres, so that I can iterate quickly.
31. As a developer, I want `./mvnw verify` to run every test locally, including the Postgres integration test, so that I can trust the build before pushing.
32. As a developer, I want CI to build and test on every pull request and push to main, so that regressions are caught automatically.
33. As a developer, I want CI to check Terraform formatting and validity without AWS credentials, so that the infrastructure code stays correct for free.
34. As the project owner, I want Terraform for ECR, ECS Fargate, RDS Postgres, a CloudWatch log group and one alarm, so that the project shows real cloud deployment skills.
35. As the project owner, I want the Terraform designed for minimum cost (public subnets, no NAT Gateway, no load balancer, smallest single-AZ database), so that applying it later stays cheap.
36. As the project owner, I want a CD workflow that deploys on merge to main using GitHub OIDC (no long-lived AWS keys), so that the pipeline follows current best practice.
37. As the project owner, I want the CD workflow to skip itself when the AWS role isn't configured, so that it never fails or costs money before I opt in.
38. As the project owner, I want nothing in AWS to be created by this project's build or CI, so that I never get a surprise bill.
39. As a recruiter or reviewer, I want a README with a clear description, a Mermaid architecture diagram, and run instructions, so that I understand the project in two minutes.
40. As a recruiter or reviewer, I want the README to explain why the Terraform isn't applied and what it would cost per month, so that the cost decision reads as deliberate.
41. As a recruiter or reviewer, I want a "what's next" section (map frontend, static GTFS, delay history, SNS alerts, Kafka/SQS ingestion, nationwide coverage), so that I can see where the design goes next.
42. As a recruiter or reviewer, I want a CI status badge, so that I can see the build is healthy.
43. As an agency data publisher, I want my agency credited in the README, so that the Creative Commons attribution terms are respected.
44. As the project owner, I want the code licensed MIT, so that others can read and reuse it.

## Implementation Decisions

**Stack**
- Java 21, Spring Boot 4.0.x, Maven with the wrapper. Postgres with Flyway migrations. The official `gtfs-realtime-bindings` library for protobuf parsing.
- Single Maven module, backend only. Verify Boot 4 compatibility of Flyway, Testcontainers, and `gtfs-realtime-bindings` at the start. If anything blocks, fall back to the latest Boot 3.5.x and record why.

**Feeds (configuration)**
- Feeds are a list in application config. Each entry has an agency ID (a short slug such as `goraleigh`), a vehicle positions URL, and an optional trip updates URL.
- v1 entries (all keyless, verified responding with protobuf on 2026-11-22):
  - `goraleigh`: `https://www.goraleighlive.org/gtfsrt/vehicles`, `https://www.goraleighlive.org/gtfsrt/trips`
  - `godurham`: `https://godurham.rideralerts.com/InfoPoint/GTFS-Realtime.ashx?Type=VehiclePosition`, `...?Type=TripUpdate`
  - `gotriangle`: `https://gotriangle.tripsparkhost.com/gtfs/Realtime/GTFS_VehiclePositions.pb`, `.../GTFS_TripUpdates.pb`
  - `chapelhill`: `http://mychtransit.org/gtfs-rt/vehiclepositions`, `http://mychtransit.org/gtfs-rt/tripupdates`
  - `gocary`: `https://www.gocarylive.org/GTFS/Realtime/GTFS_VehiclePositions.pb`, `https://www.gocarylive.org/GTFS/Realtime/GTFS_TripUpdates.pb`
- NC State Wolfline is excluded (serves `.json`, not standard protobuf).

**Modules**
- **Feed parser:** a pure function from raw protobuf bytes plus an agency ID to a list of vehicle positions or trip delays. No I/O, no Spring. Skips entities missing required fields. This is the deep module the unit tests target.
- **Feed poller:** scheduled every ~15s (configurable). Fetches all configured feeds in parallel with a per-request timeout, sends the bytes to the parser, and hands the results to the store. A failure in one agency is logged and doesn't affect the others. Exposes a "poll once now" method so tests can trigger a cycle without waiting for the schedule. Scheduling is disabled in the test profile.
- **Store:** saves the latest state. Vehicle positions are updated in place by (agency, vehicle ID). Trip delays are updated in place by (agency, trip ID). After a successful poll, the store removes an agency's vehicles that weren't in that poll, so ghost vehicles disappear. Trip delays get the same treatment.
- **REST API:** read-only endpoints over the store.
- **Live stream:** SSE broadcaster. Clients subscribe with optional agency/route filters, and the poller notifies the broadcaster after each cycle.

**Data model (latest state only)**
- `vehicle_position`: agency ID, vehicle ID, trip ID (nullable), route ID (nullable), latitude, longitude, bearing (nullable), speed (nullable), feed timestamp, updated at. Primary key (agency ID, vehicle ID). Index on (agency ID, route ID).
- `trip_delay`: agency ID, trip ID, route ID, vehicle ID (nullable), delay in seconds, feed timestamp, updated at. Primary key (agency ID, trip ID). Index on (agency ID, route ID).
- Delay comes from the trip update's next upcoming stop time update (arrival delay, else departure delay), falling back to the trip-level delay if present. Trips without any delay information are skipped.

**API contract**
- `GET /api/vehicles?agency={id}&route={id}`: both parameters optional, and route only makes sense with agency. Returns a JSON array of vehicle positions.
- `GET /api/agencies/{agency}/routes/{route}/delays`: JSON array of trip delays, most delayed first.
- `GET /api/vehicles/stream?agency={id}&route={id}`: `text/event-stream`. Each event is the filtered vehicle list after a poll.
- `GET /actuator/health`: liveness and readiness.
- Empty results return 200 with `[]`. Invalid parameters return 400.

**Local dev**
- Multi-stage Dockerfile (build with Maven, run on a slim JRE 21 image). `docker-compose.yml` with the app and Postgres, plus a Postgres healthcheck before the app starts.

**CI (GitHub Actions)**
- On PR and on push to main: `./mvnw verify` (Testcontainers runs on the hosted runner's Docker), then `terraform fmt -check` and `terraform validate` with `-backend=false`.

**Infrastructure (written, never applied)**
- Terraform targeting us-east-1: ECR repository; ECS cluster and Fargate service (0.25 vCPU / 0.5 GB, one task) in public subnets with a public IP, no NAT Gateway, no load balancer; RDS Postgres db.t4g.micro single-AZ, not publicly accessible, reachable only from the task's security group; CloudWatch log group with short retention, and one alarm (for example on ECS task count or CPU); IAM role for GitHub OIDC limited to pushing to ECR and updating this ECS service. Local state, with an S3 backend block commented out.
- CD workflow: on push to main, assume the OIDC role, build and push the image to ECR, update the ECS service. It skips itself when the `AWS_ROLE_ARN` repo variable isn't set.

**Docs**
- README with a description, Mermaid architecture diagram, how to run locally, a CI badge placeholder, agency attribution, an estimated monthly AWS cost if the Terraform is applied, the production hardening notes (load balancer, NAT, multi-AZ, remote state), and "what's next". MIT license file.

## Testing Decisions

- **What makes a good test:** tests check behavior visible from outside: what the API returns for given feed input, or what the parser produces for given bytes. They never assert on internal classes, SQL, or mocked collaborators.
- **Seam 1, the main one (HTTP API end to end):** a Spring Boot test with real Postgres via Testcontainers. Feed URLs point at a local stub HTTP server built into the JDK (no extra dependency) serving saved `.pb` fixtures. The test triggers one poll directly, then checks:
  - `GET /api/vehicles` returns the fixture's vehicles; the agency and route filters narrow results correctly.
  - `GET /api/agencies/{agency}/routes/{route}/delays` returns delays sorted most delayed first.
  - Two agencies with the same route ID don't collide.
  - When one stubbed feed returns errors or garbage bytes, the other agencies' data is still saved and served.
  - A vehicle missing from the next poll disappears from results.
  - SSE smoke test: a subscriber gets an event after a poll.
- **Seam 2 (feed parser):** plain JUnit tests on the pure parser with real fixtures captured from GoRaleigh (vehicle positions and trip updates), plus small hand-built protobuf messages for edge cases: missing position, missing route, missing delay, empty feed.
- **Fixtures:** capture real GoRaleigh vehicle positions and trip updates payloads once and commit them as test resources. Build the second agency's fixture by rewriting agency/vehicle IDs, or capture GoDurham's.
- **Not tested directly:** Terraform (covered by `fmt`/`validate` in CI), the Dockerfile (covered by `docker compose up` during manual verification).
- **Prior art:** none. The repo is new, so these tests set the patterns.

## Out of Scope

- Any frontend (map UI). It's first in "what's next".
- Static GTFS ingestion (route names, colors, stops, shapes). The API uses raw realtime route IDs.
- Delay history or analytics. Only the latest state is stored.
- WebSocket (SSE covers the live stream), service alerts feeds, user accounts, auth, rate limiting.
- Agencies outside the Triangle, and NC State Wolfline.
- Creating any AWS resources or running `terraform apply`/`plan` against a real account.
- Creating a GitHub remote or pushing. That requires the owner's explicit OK.

## Further Notes

- Cost constraint: the project must cost $0. Everything runs locally or on GitHub Actions free minutes (public repo). If the Terraform is applied later, the rough cost is about $15–25/month (Fargate 0.25 vCPU plus db.t4g.micro plus about $3.60 for the public IPv4 address, plus small CloudWatch and ECR charges). The README should state this and how to tear it down.
- The feeds are listed under Creative Commons Attribution on the GoDurham developer resources page (https://godurhamtransit.org/developer-resources). Credit each agency in the README.
- Feed availability is outside our control. A bad feed is logged and skipped, never fatal.
- Done means: `docker compose up` serves live vehicle data from the Triangle feeds, `./mvnw verify` passes locally, and CI, Terraform and README are in place. The final report must say what works, what wasn't verified, and the AWS cost notes.
