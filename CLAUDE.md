# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Debloater: a Kotlin/Jetpack Compose Android app (minSdk 30, Android 11–17) that disables/uninstalls packages without root via Shizuku. Read `HANDOFF.md` first: it has the current release state, decisions not to re-debate and approaches that already failed.

## Commands

```bash
python3 tools/check_translations.py                 # string coverage + format-arg check for all 11 locales (CI runs it)
./gradlew testDebugUnitTest                         # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests 'io.github.j0p3s.debloater.MergedListTest'          # one class
./gradlew testDebugUnitTest --tests 'io.github.j0p3s.debloater.MergedListTest.someTest' # one test
./gradlew assembleDebug                             # app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug
tools/update_uad_list.sh / tools/update_adl_list.sh # refresh the list copies bundled in assets/
```

CI (`.github/workflows/build.yml`) runs the same order: translations → tests → assembleDebug → lint, then checks that `assembleRelease` *fails* without signing credentials. Local builds in a cloud container need the SDK at `/opt/android-sdk` and a Maven mirror init script in `~/.gradle/init.d/`; a fresh container may have neither.

## Signing and releases

- Never produce or hand out a locally signed release APK, and never generate a new keystore. Release signing happens only in CI from the `ANDROID_KEYSTORE_BASE64`/`ANDROID_KEYSTORE_PASSWORD` secrets; the certificate is pinned in `config/release-certificate.sha256`. `preReleaseBuild` deliberately fails when credentials are missing.
- Debug builds use the `io.github.j0p3s.debloater.debug` app ID so they coexist with the release app.
- Release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, add `docs/RELEASE-<versionName>.md` (CI fails without it; it becomes the release notes), add a `CHANGELOG.md` entry and update the README "Current release" links. Then trigger the "Build APK" workflow with `publish_release=true` (pushing tags is blocked by the session git proxy).
- Work happens on `ccr-d8c5609f-bq6ov9`, not `main` (stale). Other sessions push there too: pull first, never force-push.

## Architecture

Source root: `app/src/main/java/io/github/j0p3s/debloater/`.

- **Privileged shell (`shizuku/`)**: `ShellService` is a Shizuku UserService running as shell/root, exposed through `aidl/.../IShellService.aidl` (`exec`, `execCache` with a shorter timeout, typed `leftovers` actions, `installApks` via pm install sessions with streamed FDs, `copyFile`). `ShizukuManager` (singleton, init in `DebloaterApp`) owns status/permission/binding and offers suspend `exec`/`execCache`/`installApks`/`copyFileAsShell`. Package changes use `pm` commands here; leftovers use validated filesystem operations. The optional `cache/SettingsCacheService` instead uses Accessibility to navigate Settings after explicit consent. Shizuku instantiates `ShellService` by class name, so `proguard-rules.pro` keeps it; keep that rule in sync if it is renamed.
- **Package data (`data/`)**: `DebloatDatabase` merges the bundled lists in `assets/` (own `debloat_list.json`, UAD-NG `uad_lists.json`, System App Remover `uad_overrides.json`, ADL `adl/*.json`) and **the stricter `SafetyLevel` always wins** (`SafetyLevel.stricter`); merging may only make the app safer. Entries can have per-SDK overrides (`DebloatEntry.forSdk`). `ListSource`/`ListFiles` pick downloaded vs built-in files; `ListUpdater` + `ListDownload` fetch new lists from GitHub, validate them and keep the previous copy on failure. `PackageRepository` lists installed apps and sizes; `ProtectedPackages` is a hard deny-list (System UI, telephony, installer, WebView, Shizuku, current launcher/keyboard…) that must never be bypassable, even in expert mode. `VendorCatalog` maps brands and package prefixes to vendors.
- **Operations (`ops/OperationEngine.kt`)**: a single engine for test runs (dry run: checks access, backups and free space, logs the commands it *would* run), disable, remove and restore. It exposes `StateFlow<OperationState>` with a log; every run's log is written via `BackupManager`.
- **Backups (`backup/`)**: always on, and not optional by design. Baseline snapshot on first run, a restore point per run, previous state per package and verified APK split copies. **A package whose backup fails is not touched.** Restore uses `install-existing` for system apps and reinstalls user apps from the APK backup.
- **UI (`ui/`)**: one `MainViewModel` (AndroidViewModel) owns the repo, engine, backups, prefs, selections and navigation. Navigation is a hand-rolled back stack of the `Screen` enum, rendered by `DebloaterRoot`, with no Navigation library. State lives in the ViewModel so selections and running operations survive the activity recreation caused by language changes. `CacheController` runs the cache scanner (bounded workers, `BoundedCacheReader` timeouts, `--cache-only` clears via `execCache`, or the opt-in Settings assistant). `LeftoverController` owns App leftovers; its operations exclude simultaneous cache/debloat work. `leftovers/LeftoverStore` checks ownership and strict paths, moves to persistent shared recovery, restores without overwrites and purges only after separate confirmation.
- **User-facing text**: use string resources (`UiMessage` wraps a res ID + args for ViewModel messages). Saved enum names, backup JSON, shell commands and operation logs stay in English and language-independent.

## Localization

`res/values*/strings.xml` covers 11 locales (Arabic is RTL; Indonesian is `values-in`; Chinese is `values-zh-rCN`). Any string change must be added to every locale and pass `tools/check_translations.py`, keeping numbered placeholders (`%1$s`). A new locale also needs `res/xml/locales_config.xml` and an autonym in `ui/components/LanguageSetting.kt`. See `docs/LOCALIZATION.md`. Descriptions from the debloat databases stay in English.

## Licensing

GPL-3.0 because the app bundles GPL-3.0 (UAD-NG, System App Remover) and AGPL-3.0 (ADL) data; bundled list files are kept unmodified. See `NOTICE.md`.
