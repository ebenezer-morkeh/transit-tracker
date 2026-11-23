# 03: CI: build and test on every PR and push to main

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** A GitHub Actions workflow that runs `./mvnw verify` (including the Testcontainers tests, using the hosted runner's Docker) on every pull request and every push to `main`. No GitHub remote is created by this ticket; the workflow is verified by running the same command locally and by validating the workflow syntax.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Workflow triggers on `pull_request` and `push` to `main`
- [ ] Sets up Java 21 (Temurin) with Maven dependency caching
- [ ] Runs `./mvnw verify`
- [ ] Workflow YAML passes a syntax check (for example `actionlint`, if available)
- [ ] Nothing is pushed and no remote is created
