# 0002: Store latest state only

Status: accepted (2026-11-22)

`vehicle_position` and `trip_delay` hold one row per vehicle / trip, upserted on every poll; rows missing from an agency's latest successful poll are deleted. No history table.

Why: v1's endpoints only ask "where is it now" and "how late is it now". History means retention jobs and a growing database for features nobody has asked for. Delay history is in the README's "what's next"; adding it later is a new append-only table, not a change to these.
