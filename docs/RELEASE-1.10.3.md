# Debloater 1.10.3 — cache responsiveness

- Scan only the selected app type, with two bounded workers instead of serial queries across every app.
- Show partial results as they arrive. Each caller waits at most eight seconds for a storage-stat query; blocked Binder calls keep their worker slots until they actually finish, preventing unbounded retries.
- Cancel measurements without waiting for blocked Binder calls. Unavailable measurements stay unknown, not zero.
- Cache-only Shizuku subprocesses have a 15-second wait limit instead of the general 180-second limit. A timeout reports an unknown outcome and stops the batch; Android may still finish an already-requested clear.
- Stop clearing after the current command; show the operation phase and current app.
- Skip known-empty caches and refresh only targeted non-empty/unknown groups after clearing.
- Preserves cache-only flags, per-user scope, app data and accounts. Does not use global trim-caches or raw filesystem deletion.

Version 1.10.3, code 15. Existing app ID and permanent signer retained. On-device performance and visual checks remain pending; no numeric speedup is claimed.

## Installation and verification
Download **Debloater-v1.10.3.apk**. Installs over 1.10.2 using the same permanent signing certificate and keeps app data/backups.
CI runs translation checks, timeout/cancellation unit tests, debug build, Android lint, release signing guard, signed release build and certificate verification. APK integrity hashes are attached in **SHA256SUMS.txt**.

Measurements still use Android StorageStatsManager: Shizuku does not bypass its latency. Clearing already uses Shizuku.
A clear already handed to Android may finish after cancellation/timeout, so timeout is never reported as success.
