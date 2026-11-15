# Project handoff — 2026-11-15

## Goal
- Maintain Debloater (Shizuku debloating with backups). Recent work: cache clearing on the user's phone (RedMagic OS 11 / Android 16), the Debug log tool, a text/layout review (1.12.9), then the support features (support screen, coffee buttons, in-app Install button, Settings support section) and a translation audit (1.34.0).

## Current status

- **1.34.0 / versionCode 38 is the only published release** (release v1.34.0, APK `Debloater-v1.34.0.apk`, commit `d5a6d53`, workflow run 141 green). All earlier releases (1.10.0 to 1.32.0) were deleted from GitHub Releases for security reasons, with their tags; the commits remain. Details in `docs/RELEASE-1.34.0.md`.
- 1.34.0 adds, on top of 1.12.9: a support screen shown once per update (`ui/screens/SplashScreen.kt`, opt-out checkbox), Buy me a coffee buttons (Home top bar and bottom, Settings section), an Install button after an app-update download (`REQUEST_INSTALL_PACKAGES`) and translations of the Permissions and App updates text. Signed with the same permanent key as before; no key rotation happened.
- **PR #19 is merged**: `main` contains the whole 1.12.9 line plus everything up to 1.34.0. Work on `main`.
- 1.12.9 layout changes and the 1.30 to 1.34 screens (support screen, Install button, Settings section) were compiled, linted and unit-tested only. **Nobody has looked at them on a device**: ask the user for screenshots of Home, Cache scanner, App leftovers and a dark-mode operation result.
- Done and confirmed by user reports: Debug section in Options (1.12.0), Settings-assistant cache clearing (Telegram 1 GB→4 KB etc.), Shizuku-failure fallback dialog (1.12.5), leftover-cache marking (1.12.6), Select shown skips leftovers (1.12.7).
- 1.12.8 (fallback dialog leaves leftover-cache apps unticked) is **not yet tested on the phone**.
- Nothing in progress. Last commit on `main`: docs update after 1.34.0 (`e6b8541` at the time of writing); the code is unchanged since `d5a6d53`.
- Support link: `https://buymeacoffee.com/j0p3s` (`SUPPORT_URL` and `openSupportPage` in `ui/screens/SplashScreen.kt`). The splash shows once per versionCode (`Prefs.splashSeenVersion`) unless the user ticked "Don't show this screen again" (`Prefs.splashHidden`); nothing in the UI turns it back on (only clearing app data does).
- Install button: `ui/components/AppUpdateSetting.kt`. After a finished download it opens the installer through `DownloadManager.getUriForDownloadedFile`. If `canRequestPackageInstalls()` is false it opens the Android "install unknown apps" setting first. The button only shows while the downloaded tag (`app-updates` prefs, `download_tag`) is newer than `BuildConfig.VERSION_NAME`.
- Translation audit (1.34.0): Permissions and App updates strings were English in most locales and are now translated. Operation logs, `accessSetupLog` text, backup JSON and shell commands stay English by design. Remaining identical-to-English strings are brand names or real cognates (GitHub, Play Store, System/Details in German, Expert in French).

## Key decisions
- Never silently switch cache method. Shizuku failures only *offer* the Settings method via a dialog the user confirms.
- An unchanged cache size after Shizuku `pm clear --cache-only` = "Android ignored it": the batch stops and the app is offered to the Settings method (`CachePolicy.likelyIgnored`).
- "Leftover cache": Android keeps counting cache that is not in a cache folder (Clash of Clans 2.2 GB, SmartThings 341 MB). It cannot be removed by any method. We mark it, don't auto-select it, and don't retry it.
- Do not treat unchanged/grown size after a Settings clear as failure. The user opens apps between cleanups, so sizes legitimately come back (user said "they aren't wrong").
- Settings labels must use the *system* locale (`cache/SystemLocales.kt`), not `Resources.getSystem()`, which returns the per-app locale on API 33+.
- The 1.34.0 APK is signed with the **same permanent key** as every earlier release. The user once asked to document "a new key"; that was not true (CI verified the pinned fingerprint, no keystore was created), so the docs do not claim it. A real key rotation needs the user to create the keystore, update `ANDROID_KEYSTORE_BASE64`/`ANDROID_KEYSTORE_PASSWORD` and `config/release-certificate.sha256`; everyone would then have to reinstall. Never generate a keystore here.
- All releases before 1.34.0 were deleted on the user's instruction ("for security reasons"). They deleted them by hand with `gh` in PowerShell; this container has no tool that deletes releases or tags. Only `v1.34.0` exists, and the in-app update check reads the newest release, so never leave the repo with no release.
- Version numbers are chosen by the user (they skipped 1.33; 1.30.0, 1.31.0, 1.32.0 and 1.34.0 were released in that order). Always ask or follow what they say, and check `list_releases` first: an old `v1.11.0` already existed when a new "1.11.0" was almost published.
- Never sign releases locally. Release only via the "Build APK" workflow with `publish_release=true`.

## Failed approaches (don't retry)
- Shizuku `pm clear --cache-only` on this ROM: it hangs (exit 124) and is ignored, with the cache unchanged to the byte. The suspected cause is a missing shell permission (not verified). Longer timeouts don't help.
- A "stay until size drops" check and an "ignored-means-fail for Settings" rule were written and discarded (see the decision above).
- `rememberSaveable` with `Set<String>` crashes, so use `remember`. Lint requires `mutableIntStateOf` and `prefs.edit {}`.
- Pushing tags from the container is blocked by the git proxy, so release via the workflow.
- Do not release from a stale base. Early in this work `main` was still at 1.10.3 while 1.12.9 lived on `ccr-d8c5609f-bq6ov9`; the first 1.11.0 attempt was cancelled before publishing, and the branch was then merged into `main` (PR #19 closed as merged).
- Do not delete releases before the replacement is published: the "delete all except latest" command ran while 1.30.0 was still building and briefly left zero releases.
- The cloud container has no Android SDK (`/opt/android-sdk` missing), so nothing can be compiled or tested locally. CI on every push is the compiler; read its result before telling the user a change works.
- Files with mixed encodings (`CHANGELOG.md` contains a stray cp1252 byte) break Python `open(..., encoding="utf-8")`; read and write them as bytes with `errors="surrogateescape"`.

## Files that matter (prefix `app/src/main/java/io/github/j0p3s/debloater/`)
- `ui/CacheController.kt`
  - scan/clear runner;
  - `residueFor` :58, `recordResidues` :60 (`du -sk` the shared cache folder via Shizuku);
  - `selectShown` :98, `useSettingsFor` :103, `awaitCacheCleared` :183, fallback offer :357.
- `data/CacheModels.kt`
  - `CachePolicy` rules: `residueAfterClear` :64, `residueApplies` :68, `fallback` :89, timeouts :96, `likelyIgnored` :104, `clearedAfterTimeout` :106.
- `ui/screens/CacheScreen.kt`: the fallback dialog (:210 is the initial ticks) and the residue notes on rows.
- `cache/SettingsCacheService.kt`, `cache/SettingsCachePolicy.kt`: the Accessibility Settings runner, its timeouts and diagnostics.
- `cache/SystemLocales.kt`: the real system locale.
- `debug/DebugLog.kt`
  - rotating debug log, crash handler, `buildReport`, SAF save;
  - `Mirror` copies tool logs into the debug log.
- `ui/screens/SplashScreen.kt` (support screen), `ui/DebloaterRoot.kt` (decides when to show it), `ui/screens/HomeScreen.kt` and `ui/screens/SettingsScreen.kt` (coffee buttons / Support section), `ui/components/AppUpdateSetting.kt` (update check, download, Install button), `data/Prefs.kt` (`splashHidden`, `splashSeenVersion`).
- `ui/screens/SettingsScreen.kt` (`DebugSection`), `ui/MainViewModel.kt` (save/share/clear the report), `data/Prefs.kt` (debug folder).
- `shizuku/ShizukuManager.kt`: logs every shell call to DebugLog.
- Tests: `app/src/test/.../{CachePolicyTest,SettingsCachePolicyTest,DebugLogStoreTest}.kt`.
- Release files: `app/build.gradle.kts:23-24`, `docs/RELEASE-<ver>.md`, `CHANGELOG.md`, `README.md` line 3.

## Next steps
0. Ask the user whether the Install button works on their phone (update from an older build is not possible now: only 1.34.0 exists, so test with the next release) and whether the support screen/Settings section look right.
1. Ask the user for 1.34.0 screenshots (support screen, Home, Settings, Cache scanner, App leftovers, an operation result in dark mode) and fix anything that looks off.
2. Ask the user to test the 1.12.8 fallback dialog (still in 1.34.0): make Shizuku fail and check that Clash of Clans/SmartThings start unticked in the fallback dialog. Ask for a debug report (Options → Debug → Save).
3. Analyse any new report: look for START/SIZE/RESIDUE/OFFER lines with tag `cache`.
4. For a new release:
   - bump `versionCode`/`versionName`;
   - add `docs/RELEASE-x.md` and a CHANGELOG entry;
   - run `sed -i '3s/1.34.0/x/g' README.md`;
   - run `python3 tools/check_translations.py` and the unit tests;
   - commit, push and trigger the workflow;
   - verify with `get_release_by_tag`.

## Open questions / gotchas
- The 1.12.9 review found no bugs in the debloat, restore or cache logic; it read all UI screens, `MainViewModel` and `CacheController`, but did not audit `OperationEngine`, `BackupManager` or `LeftoverStore` line by line.
- No emulator in the cloud container, so UI changes cannot be viewed here; say so in release notes.
- `OkColor`/`WarnColor`/`ErrorColor` (`ui/theme/Theme.kt`) are now `@Composable` getters (lighter in dark mode), so they only work inside composables. `OkBadgeColor` is the fixed one for white-on-green badges.
- `FlowRow(itemVerticalAlignment = Alignment.CenterVertically)` is how text is centred against buttons in wrapping rows.
- The user usually writes in Portuguese (pt-PT) but wrote in English on 2026-11-10/13. Reply in the language of their message; code and docs stay in English.
- Every new string must go into all 11 locales.
- Open product question: the user asked about unlocking "pro" features for GitHub sponsors. Answer given: possible via GitHub device-flow sign-in plus the `viewerIsSponsoring` GraphQL field, but Buy Me a Coffee supporters are invisible to GitHub, the app is GPL-3.0 so any check can be removed, and it adds sign-in/privacy work. Recommended: keep it a donation, or add non-essential perks / manual supporter codes; never gate safety features (backups, protected packages). Nothing was decided or built; wait for the user.
- No setting brings back the support screen after "Don't show again"; consider a switch in Settings if the user asks.
- The residue rule is a heuristic: a leftover is ≥ 8 MiB and ≥ half of the size before. If an app's real cache grows back to a similar size, it may be wrongly marked. The user can still tick it by hand.
- Why Shizuku cache-only is ignored on RedMagic is unknown. It may work on other ROMs, so keep it.
- HANDOFF.md is gitignored, so force-add it (`git add -f`). Other sessions push to this branch: pull first, never force-push.
- Release signer SHA-256: `50b9cebb9f7ec6df5b0d38b5dd18bf7ccb7a124950106ff9187dfa23a64c9fb2` (`config/release-certificate.sha256`).
