# Project handoff — 2026-11-05

## Goal
- Maintain Debloater: safe Shizuku debloating with tests, confirmations, backups, vendor recommendations and cache management.
- Add SD Maid-style Settings cache clearing and reviewed cleanup of uninstalled-app leftovers.

## Current status
- Release target **1.11.0 / versionCode 16**, package `io.github.j0p3s.debloater`, minSdk 30, compile/target 36.
- Both tools implemented; PR #8: https://github.com/j0p3s/Debloat-app-android/pull/8 . User explicitly authorized merge and publication.
- Verify publication/build state at https://github.com/j0p3s/Debloat-app-android/releases and Actions before doing more release work.
- README, changelog, Help, release/setup/architecture/localization docs and all 11 translations updated.
- Earlier 1.10.3 added bounded cache scans, progress/cancel and targeted refresh; retain those safeguards.
- No physical phone is connected; device/OEM behavior and speed remain unverified. Do not claim measured performance.

## Key decisions
- Preserve CI's permanent signing key and certificate pin; never generate a replacement keystore or distribute a locally signed release.
- Keep backed-up uninstall/restore features in the GitHub app as well as recoverable disabling. The separate old SafeDebloat app was removed; never revive it.
- Cache method is explicitly chosen: Shizuku cache-only commands or optional Accessibility Settings assistance; never silently switch methods.
- The Settings assistant requires manual Accessibility enablement and per-batch warning/target confirmation; permission enablement never starts cleaning.
- Only inspect Settings; verify the target app and exact localized controls. No Clear data, dialogs, coordinate taps or blind key presses. Unknown screens/results stop the batch.
- Floating Cancel stops UI actions immediately. Confirmed means Settings disabled the cache button after pressing; measured space is separate.
- App leftovers scan only package-named current-user primary Android/data, Android/obb and Android/media folders. This is a conservative subset of SD Maid, not its full forensic/ROM coverage.
- `pm list packages -u --user all` protects installed, disabled, other-user and retained owners; failed inventory blocks cleanup. Recheck before moving.
- Reviewed leftovers move atomically into shared `.DebloaterRecovery`, with location metadata saved first. Move failure leaves source intact; recovery does not free space.
- Restore never overwrites/merges existing folders. A separate irreversible-delete confirmation purges only the selected recovery record.
- Reject traversal, symlinks and oversized/deep trees. Stop file work between bounded operations; an active filesystem call completes first.

## Failed / unsuitable approaches
- Serial whole-device scans, 180-second cache commands and hidden full rescans felt stuck; retain scoped two-worker measurement and cache-specific timeout/targeted refresh.
- Binder calls may ignore coroutine cancellation; keep worker slots occupied until actual blocking calls finish, even after caller timeout.
- `Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS` is not a public SDK constant; use public ACTION_ACCESSIBILITY_SETTINGS.
- Mutually referring cache/leftover controllers need explicit Kotlin property/return types to avoid recursive type inference.
- Local Android SDK is 35, project requires 36; CI provides full builds. No phone/emulator validation was available.

## Files that matter
- `CLAUDE.md` — build, architecture, release, signing and localization guidance.
- `app/build.gradle.kts`, `.github/workflows/build.yml`, `config/release-certificate.sha256` — version, gated publication and permanent signer.
- Source prefix for the next five bullets: `app/src/main/java/io/github/j0p3s/debloater/`.
- `cache/SettingsCacheService.kt`, `cache/SettingsCachePolicy.kt` — opt-in UI runner, stop overlay and conservative selectors.
- `leftovers/LeftoverStore.kt` — ownership/path validation, scan, durable recovery, non-overwriting restore and selected purge.
- `shizuku/ShellService.kt`, `shizuku/ShizukuManager.kt` — privileged commands and typed leftovers RPC; AIDL at `app/src/main/aidl/io/github/j0p3s/debloater/IShellService.aidl`.
- `ui/CacheController.kt`, `ui/LeftoverController.kt`, `ui/MainViewModel.kt` — selections, operations, progress and mutual exclusion.
- `ui/screens/{CacheScreen,LeftoversScreen,HomeScreen,HelpScreen}.kt` — method/permission warnings, review/recovery UI and navigation.
- `app/src/main/res/xml/cache_accessibility_service.xml`, `app/src/main/AndroidManifest.xml` — narrow Accessibility declaration.
- `app/src/test/java/io/github/j0p3s/debloater/{SettingsCachePolicyTest,LeftoverStoreTest,BoundedCacheReaderTest,CachePolicyTest}.kt` — safety/regression coverage.
- `README.md`, `CHANGELOG.md`, `docs/{RELEASE-1.11.0,SETTINGS-CACHE-ASSISTANT,APP-LEFTOVERS,LOCALIZATION}.md` — user/release docs.

## Next steps
1. Check Git status, fetch origin and inspect PR #8 / release v1.11.0 / latest Actions results; finish any still-running publication before starting another release.
2. Test the published APK on disposable user and permitted system apps: scopes, warnings, stop, lock/revoke Accessibility and unknown Settings layouts.
3. Test leftovers with disposable folders: owner protection, restart/recovery, denied access, conflicting restore and separately confirmed purge.
4. Record device/Android/OEM details and timings; add manufacturer-specific selectors only with verified screen evidence and tests.

## Gotchas
- Default branch is `ccr-d8c5609f-bq6ov9`; other sessions push here. Fetch first, never force-push.
- Windows Git may need per-command core.longpaths/safe.directory; use GitHub noreply author email, not global Git config changes.
- HANDOFF.md is ignored; explicitly force-add requested updates. Published APK must match signer SHA256 `50b9cebb9f7ec6df5b0d38b5dd18bf7ccb7a124950106ff9187dfa23a64c9fb2`.
