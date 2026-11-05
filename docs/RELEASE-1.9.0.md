# Debloater 1.9.0

## New cache tools
- User cache and System cache entries on the main dashboard.
- Scan all installed app caches for the current Android user, with separate user/system totals and a combined measured total.
- Shared UID caches are counted once per storage volume. Shared groups are selected together, and unavailable measurements are shown explicitly.
- Sort caches largest/smallest first or names A–Z/Z–A; search app/package names and filter out empty or unmeasured caches.
- Select visible results or individual groups; review all selected apps before clearing.
- Cache-only command support detection, per-command success/failure logs, log copying, and automatic rescan after clearing.
- New interface text translated into English, Spanish, French and German.

## Installation
Download **Debloater-v1.9.0.apk**. Requires Android 11 or later. Start Shizuku and grant its permission for cache clearing and package operations. Usage access is required to measure other apps; grant it through Shizuku or Android Settings.

This release retains application ID `io.github.j0p3s.debloater` and the permanent signer introduced in 1.8.0, allowing an in-place update from 1.8.0 with app data retained. Versions through 1.7.0 used temporary keys: export backups before the one-time uninstall/reinstall migration.

## Cache limits
- Cache clearing preserves app data and accounts but permanently deletes temporary cache. Debloat backups do not restore cache.
- Cache-only clearing is enabled only when Android advertises the command. ROM permission failures are reported; there is no fallback to clearing app data.
- Totals cover app caches for the current user, not other users, protected OS/recovery caches, or the read-only system partition. Unavailable sizes are excluded.
- Running apps may immediately rebuild cache. Actual freed storage may differ from measured cache size.
- On-device visual and behavior checks remain pending.

## Release metadata
- Version: **1.9.0**
- Version code: **11**
- Signing: existing permanent release certificate, verified against `config/release-certificate.sha256` by CI.
- Validation: translation coverage, unit tests, debug lint/build, release signing guard, signed release build and certificate verification are required before publishing.
