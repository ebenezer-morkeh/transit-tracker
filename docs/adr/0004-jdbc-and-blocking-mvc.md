# 0004: Spring JDBC (JdbcClient) and blocking Spring MVC, not JPA or WebFlux

Status: accepted (2026-11-22)

Persistence is hand-written SQL through `JdbcClient` in package-private repositories. The web layer is Spring MVC on servlet threads, with `SseEmitter` for the stream.

Why not JPA: the workload is bulk SQL with no object graph: `INSERT ... ON CONFLICT` upserts, "delete rows this poll didn't contain" (`<> ALL(?)`), and 226k-row batch inserts of the schedule. JPA makes each of these slower or awkward and adds a persistence context nothing needs.

Why not WebFlux/Reactor: load is five feeds every 15 seconds and a handful of API clients. A reactive stack adds a second programming model for no measurable gain. Java 21 virtual threads are the upgrade path if concurrency ever matters.
