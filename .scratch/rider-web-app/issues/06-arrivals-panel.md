# 06: Stop arrivals panel

Spec: `.scratch/rider-web-app/spec.md` (Web app)

**What to build:** Selecting a stop, or opening a link with `agency` and `stop`, shows that stop's arrivals grouped by route, refreshed every 15 s. Each arrival shows "Due" or "N min", the clock time, and its live/scheduled status with lateness.

**Blocked by:** 03, 05

**Status:** done

- [x] "Due" under 1 minute, otherwise "N min"; clock time in America/New_York
- [x] "live · N min late" / "live · on time" / "live · N min early" / "scheduled"
- [x] "Live times unavailable for {agency}, showing schedule" notice when flagged; a clear empty state with no arrivals
- [x] Re-fetches every 15 s; stops refreshing when no stop is selected
- [x] Vitest: minutes/"Due" wording, clock formatting, late/early/on-time wording
- [x] Arrivals are a text list that screen readers announce sensibly; updates don't steal focus

## Notes (2026-11-27)

- Focus moves to the stop heading when a stop is picked, not on refreshes; the list isn't an `aria-live` region, so refreshes are silent.
- Checked by hand against live GoRaleigh data at phone size.
