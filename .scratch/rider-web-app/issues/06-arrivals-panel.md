# 06: Stop arrivals panel

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** Selecting a stop, or opening a link with `agency` and `stop`, shows that stop's arrivals grouped by route, refreshed every 15 s. Each arrival shows "Due" or "N min", the clock time, and its live/scheduled status with lateness.

**Blocked by:** 03, 05

**Status:** ready-for-agent

- [ ] "Due" under 1 minute, otherwise "N min"; clock time in America/New_York
- [ ] "live · N min late" / "live · on time" / "live · N min early" / "scheduled"
- [ ] "Live times unavailable for {agency}, showing schedule" notice when flagged; a clear empty state with no arrivals
- [ ] Re-fetches every 15 s; stops refreshing when no stop is selected
- [ ] Vitest: minutes/"Due" wording, clock formatting, late/early/on-time wording
- [ ] Arrivals are a text list that screen readers announce sensibly; updates don't steal focus
