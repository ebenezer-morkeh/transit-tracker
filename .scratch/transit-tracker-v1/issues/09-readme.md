# 09: README

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** A README that lets a recruiter or reviewer understand, run, and judge the project in a couple of minutes.

**Blocked by:** 06, 07, 08

**Status:** ready-for-agent

- [ ] What it does (five Triangle agencies, live positions + delays, REST + SSE)
- [ ] Mermaid architecture diagram (feeds → poller → parser → Postgres → REST/SSE; AWS target layout)
- [ ] How to run: `docker compose up`, run from IDE, `./mvnw verify`
- [ ] API reference with example requests/responses for every endpoint
- [ ] CI badge placeholder
- [ ] Infrastructure section: written but not applied (and why), estimated monthly cost if applied (~$15–25), how to apply and tear down, production hardening notes (load balancer, NAT, multi-AZ, remote state)
- [ ] Agency attribution (Creative Commons, per the GoDurham developer resources page)
- [ ] "What's next": React + Leaflet map, static GTFS (route names/shapes), delay history, SNS delay alerts, Kafka/SQS ingestion, nationwide via Mobility Database
