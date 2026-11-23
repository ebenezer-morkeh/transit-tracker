# 07: Live vehicle stream over SSE

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** API consumers can subscribe to `GET /api/vehicles/stream?agency={id}&route={id}` (both optional, same rules as the list endpoint) and get a `text/event-stream` event with the filtered vehicle list right after every poll cycle.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Clients subscribe with optional agency/route filters
- [ ] Poller notifies subscribers after each cycle; each client gets only its filtered vehicles
- [ ] Disconnected clients are cleaned up (no leak, no errors on later polls)
- [ ] End-to-end smoke test: a subscriber gets an event containing stubbed vehicles after a triggered poll
