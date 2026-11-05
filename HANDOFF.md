# Project handoff — 2026-11-05

## Goal
- Maintain Debloater: safe Android debloating through Shizuku, with test logs, confirmations, backups/restore, vendor recommendations and scoped cache management.
- Latest work makes cache operations responsive and cancellable without risking app data.

## Current status
- Published **1.10.3 / versionCode 15**, package `io.github.j0p3s.debloater`; Android 11+ (minSdk 30, compile/target 36).
- Release: https://github.com/j0p3s/Debloat-app-android/releases/tag/v1.10.3 ; PR #6 merged at `9a6cde5a451ed6ea00d766e4c9032e0313b70e83`.
- Tests, translation checks, lint, debug/release builds and permanent-signature verification passed. Final push CI: https://github.com/j0p3s/Debloat-app-android/actions/runs/41774932995 .
- README, CHANGELOG, release notes and 11 translations updated; no app implementation work remains pending.
- 1.10.1 fixed bottom navigation/onboarding overlap; 1.10.2 added User/System/Both cache scope; 1.10.3 added bounded waits, progress and cancellation.
- Default branch `ccr-d8c5609f-bq6ov9` also has later documentation commits through `8dd43a1`; other sessions work here, so fetch before changes.

## Key decisions
- Preserve the permanent signing identity; release signing uses existing CI secrets and certificate pin. Never generate a replacement keystore.
- Disable is the recoverable debloat path; the GitHub app also retains its existing backed-up uninstall/restore features. Do not remove those based on earlier SafeDebloat requirements.
- Cache clearing uses only validated `pm clear --user <id> --cache-only <package>` through Shizuku, after checking command support; never fall back to plain `pm clear`.
- Cache sizes use Usage Access + StorageStatsManager; group shared UID/volume once, current Android user only. Protected OS/recovery caches are outside scope.
- Scan only the selected scope with two actual blocking workers, 8-second caller waits and unknown results on failure. Keep worker slots occupied until blocking calls really finish.
- Cache shell commands have a 15-second timeout; timeout means unknown outcome and stops the batch. Clearing cancellation finishes the active bounded command before stopping.
- Refresh only affected cache groups after clearing; avoid a hidden full package-size reload. Scan cancellation is immediate.

## Failed / unsuitable approaches
- Serial scans across all UIDs ignored the chosen scope and felt stuck; replaced with scoped, bounded parallel measurements.
- Generic 180-second per-app shell waits plus whole-device rescans made clearing excessively slow; cache commands now have their own timeout and targeted refresh.
- Cancelling a coroutine cannot reliably interrupt Android Binder calls; releasing slots on timeout would accumulate orphan workers. Do not reintroduce that pattern.
- The old separate SafeDebloat app had a different ID/signer; its release/tag/branch/artifacts were deliberately removed. Never revive or publish them.
- Local SDK is 35 while this project needs 36; use CI unless the local SDK is updated. Git may require per-command safe.directory and core.longpaths on Windows.

## Files that matter
- `CLAUDE.md` — build, architecture, safety, localization and release guidance; read before editing.
- `app/build.gradle.kts` — app version, SDK levels and signing configuration.
- Source prefix below: `app/src/main/java/io/github/j0p3s/debloater/`.
- `ui/CacheController.kt`, `data/BoundedCacheReader.kt` — scoped measurement, worker limits, progress, cancellation and clear orchestration.
- `data/CacheModels.kt`, `ui/screens/CacheScreen.kt` — grouping/policy/scope/sorting and cache UI.
- `shizuku/ShellService.kt`, `shizuku/ShizukuManager.kt` — privileged execution and separate cache timeout; interface: `app/src/main/aidl/io/github/j0p3s/debloater/IShellService.aidl`.
- `ui/MainViewModel.kt` — cache ownership and targeted post-clear package/storage refresh.
- `app/src/test/java/io/github/j0p3s/debloater/{CachePolicyTest,BoundedCacheReaderTest}.kt` — cache-only safety, scope/sorting and blocked-worker/cancellation regression coverage.
- `README.md`, `CHANGELOG.md`, `docs/RELEASE-1.10.3.md`, `docs/LOCALIZATION.md` — current product/release/localization documentation.
- `.github/workflows/build.yml`, `config/release-certificate.sha256`, `tools/check_translations.py` — CI, signing pin and locale validation.
- Parent folder `release-1.10.3/` — verified APK, SHA256SUMS.txt and published release body (local artifacts, outside repository).

## Next steps
1. Run `git status --short` and fetch origin; confirm the default branch and read this handoff plus `CLAUDE.md` before new work.
2. Test installed 1.10.3 on the user's phone: User/System/Both scans, progress, cancel, timeout handling and targeted refresh after clearing.
3. Record observed timings/device details before proposing further performance changes; no real-phone speed benchmark has been collected.
4. For future code changes, run translation validation, relevant unit tests, build and lint; update version/docs and publish only when requested.

## Open questions / gotchas
- Phone performance and visual verification remain pending; do not claim a measured speedup from CI tests.
- Timed-out Android operations may complete later; report unknown rather than success or zero bytes. Cache contents are temporary and are not recoverable app-data backups.
- Permanent certificate SHA256: `50b9cebb9f7ec6df5b0d38b5dd18bf7ccb7a124950106ff9187dfa23a64c9fb2`.
- Published APK SHA256: `321c09e9aed4392a99f55badbbae8a48681b2fc035cc059273b408d29d54087c`.
- HANDOFF.md is ignored by existing .gitignore; explicitly force-add this requested handoff when committing. Never force-push or overwrite another session's changes.
