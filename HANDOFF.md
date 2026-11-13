# Project handoff — 2026-11-13

## Goal
- Maintain Debloater (Shizuku debloating with backups). Recent work: cache clearing on the user's phone (RedMagic OS 11 / Android 16), the Debug log tool, and a text/layout review (1.12.9).

## Current status
- **1.12.9 / versionCode 34 is published** (release v1.12.9, APK `Debloater-v1.12.9.apk`, commit `61a4037`, workflow run 121 green). It is a text and layout review with no behaviour change; details in `docs/RELEASE-1.12.9.md`.
- **PR #19** (`ccr-d8c5609f-bq6ov9` → `main`, "Bring main up to date with v1.12.9") is open; the user marked it ready for review on 2026-11-13. CI is green. Only the user merges it. After a merge, keep working on this branch as before (never force-push).
- 1.12.9 layout changes were compiled, linted and unit-tested only. **Nobody has looked at them on a device**: ask the user for screenshots of Home, Cache scanner, App leftovers and a dark-mode operation result.
- Done and confirmed by user reports: Debug section in Options (1.12.0), Settings-assistant cache clearing (Telegram 1 GB→4 KB etc.), Shizuku-failure fallback dialog (1.12.5), leftover-cache marking (1.12.6), Select shown skips leftovers (1.12.7).
- 1.12.8 (fallback dialog leaves leftover-cache apps unticked) is **not yet tested on the phone**.
- Nothing in progress.

## Key decisions
- Never silently switch cache method. Shizuku failures only *offer* the Settings method via a dialog the user confirms.
- An unchanged cache size after Shizuku `pm clear --cache-only` = "Android ignored it": the batch stops and the app is offered to the Settings method (`CachePolicy.likelyIgnored`).
- "Leftover cache": Android keeps counting cache that is not in a cache folder (Clash of Clans 2.2 GB, SmartThings 341 MB). It cannot be removed by any method. We mark it, don't auto-select it, and don't retry it.
- Do not treat unchanged/grown size after a Settings clear as failure. The user opens apps between cleanups, so sizes legitimately come back (user said "they aren't wrong").
- Settings labels must use the *system* locale (`cache/SystemLocales.kt`), not `Resources.getSystem()`, which returns the per-app locale on API 33+.
- Never sign releases locally. Release only via the "Build APK" workflow with `publish_release=true`.

## Failed approaches (don't retry)
- Shizuku `pm clear --cache-only` on this ROM: it hangs (exit 124) and is ignored, with the cache unchanged to the byte. The suspected cause is a missing shell permission (not verified). Longer timeouts don't help.
- A "stay until size drops" check and an "ignored-means-fail for Settings" rule were written and discarded (see the decision above).
- `rememberSaveable` with `Set<String>` crashes, so use `remember`. Lint requires `mutableIntStateOf` and `prefs.edit {}`.
- Pushing tags from the container is blocked by the git proxy, so release via the workflow.

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
- `ui/screens/SettingsScreen.kt` (`DebugSection`), `ui/MainViewModel.kt` (save/share/clear the report), `data/Prefs.kt` (debug folder).
- `shizuku/ShizukuManager.kt`: logs every shell call to DebugLog.
- Tests: `app/src/test/.../{CachePolicyTest,SettingsCachePolicyTest,DebugLogStoreTest}.kt`.
- Release files: `app/build.gradle.kts:23-24`, `docs/RELEASE-<ver>.md`, `CHANGELOG.md`, `README.md` line 3.

## Next steps
1. Ask the user for 1.12.9 screenshots (Home, Cache scanner, App leftovers, an operation result in dark mode) and fix anything that looks off.
2. Ask the user to test the 1.12.8 fallback dialog (still in 1.12.9): make Shizuku fail and check that Clash of Clans/SmartThings start unticked in the fallback dialog. Ask for a debug report (Options → Debug → Save).
3. Analyse any new report: look for START/SIZE/RESIDUE/OFFER lines with tag `cache`.
4. For a new release:
   - bump `versionCode`/`versionName`;
   - add `docs/RELEASE-x.md` and a CHANGELOG entry;
   - run `sed -i '3s/1.12.9/x/g' README.md`;
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
- The residue rule is a heuristic: a leftover is ≥ 8 MiB and ≥ half of the size before. If an app's real cache grows back to a similar size, it may be wrongly marked. The user can still tick it by hand.
- Why Shizuku cache-only is ignored on RedMagic is unknown. It may work on other ROMs, so keep it.
- HANDOFF.md is gitignored, so force-add it (`git add -f`). Other sessions push to this branch: pull first, never force-push.
- Release signer SHA-256: `50b9cebb9f7ec6df5b0d38b5dd18bf7ccb7a124950106ff9187dfa23a64c9fb2` (`config/release-certificate.sha256`).
