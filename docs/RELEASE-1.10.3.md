# Debloater 1.10.3 — cache responsiveness

- Scan only the selected app type, with two bounded workers instead of serial queries across every app.
- Show partial results as they arrive. Each caller waits at most eight seconds for a storage-stat query; blocked Binder calls keep their worker slots until they actually finish, preventing unbounded retries.
- Cancel measurements without waiting for blocked Binder calls. Unavailable measurements stay unknown, not zero.
- Cache-only Shizuku subprocesses have a 15-second wait limit instead of the general 180-second limit. A timeout reports an unknown outcome and stops the batch; Android may still finish an already-requested clear.
- Stop clearing after the current command; show the operation phase and current app.
- Skip known-empty caches and refresh only targeted non-empty/unknown groups after clearing.
- Preserves cache-only flags, per-user scope, app data and accounts. Does not use global trim-caches or raw filesystem deletion.

Version 1.10.3, code 15. Existing app ID and permanent signer retained. On-device performance and visual checks remain pending; no numeric speedup is claimed.
