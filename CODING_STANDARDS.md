# Coding standards

Read at review time. These are judgement calls. Mechanical rules live in `ArchitectureTests` and fail the build, so don't re-check them here. Vocabulary comes from `GLOSSARY.md`; decisions from `docs/adr/`.

## Pure core

Parsers (`FeedParser`, `StopTimesParser`) and the delay rule (`TripDelays`) are pure: bytes or values in, records out, no I/O, no Spring, no clock reads except through parameters. New interpretation of feed data goes into a pure function with its own unit test, so feed quirks stay testable without HTTP or Postgres.

## Per-agency isolation

Anything that talks to an agency (a poll, a schedule load) is isolated per agency: bounded by a timeout, a failure is logged with the agency ID and skipped, and it never blocks or empties another agency's data. A failed or empty static load keeps the previous schedule. Review every new external call for this.

## Feed facts

Code that depends on a feed field holds for every agency in `docs/feeds.md`, or handles the agencies where it doesn't. A field present in one agency's fixture is not evidence for the others.

## SQL

SQL lives in the feature's repository, as set-based statements: upserts with `ON CONFLICT`, deletes with `<> ALL(?)`, batched inserts. One query per poll per agency, not one per row. Schema changes are a new Flyway migration; existing migrations are never edited.

## Services

Services own validation and anything that crosses a feature boundary; they stay thin where there is no logic. Errors a client caused become a domain exception in the service and a `ProblemDetail` 400 in the controller.

## Naming

Use the glossary's terms in class, method, field and test names (vehicle position, trip delay, scheduled stop time, service day, agency). A name that needs a comment to explain it is a finding.

## Tests

Behaviour is tested at the highest seam that can see it: the HTTP API end to end (Testcontainers Postgres plus a JDK stub server for feeds), or the pure function for parsing and delay math. Tests assert on responses and returned records, not on internal classes or SQL.
