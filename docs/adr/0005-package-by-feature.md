# 0005: Package by feature, controller → service → repository

Status: accepted (2026-11-22)

Packages are features: `ingest` (feed config, parser, poller), `vehicles`, `delays`, `schedule`. Inside a feature: controller → service → repository. Repositories are package-private, so another feature (or the poller) can only reach data through that feature's public `*Service`. `ArchitectureTests` enforces this.

Why: a layer-per-folder layout spreads one feature across three packages; a single flat package (v1's first cut) let controllers call repositories directly. Services stay thin where there is no logic; they exist so validation, cross-feature access and future logic have one home.
