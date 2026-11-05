# Debloater (Shizuku)

**Current release: [1.11.0](https://github.com/j0p3s/Debloat-app-android/releases/tag/v1.11.0)** · [Download APK](https://github.com/j0p3s/Debloat-app-android/releases/download/v1.11.0/Debloater-v1.11.0.apk) · [Release notes](docs/RELEASE-1.11.0.md) · [Changelog](CHANGELOG.md)

An Android app that debloats **any Android version from Android 11 to Android 17** without root, using
[Shizuku](https://shizuku.rikka.app/). It also works as a normal app manager, so you can remove any user app.

## Features

- **Languages**: English, Spanish, French, German, Portuguese, Italian, Russian, Simplified Chinese,
  Hindi, Arabic (right-to-left) and Indonesian. The app follows the device language by default.
  Choose another language in **Settings → Language**, or in Android's per-app language settings on
  Android 13+. Your choice is remembered on Android 11 and 12 too. Source app descriptions, warnings
  and technical logs can remain in English; the interface and confirmation guidance are translated.
- **Beginner guide**: on first launch a short welcome lets you pick a language, explains test → disable →
  undo, and walks through connecting Shizuku. Until Shizuku works, the home screen shows the same numbered
  setup steps with buttons for each one. **Clean up my phone** (first entry on the home screen) lists the
  Recommended apps for this phone, preselected, with Test and Disable as the main actions. A **Help** page
  (question-mark icon) answers common questions in plain language and can show the welcome again.
- **Compact dashboard**: device and storage information lead the home screen, followed by grouped
  navigation and tier statistics. The getting-started guide and secondary settings expand on demand.
  Light and dark themes use an emerald palette; selected apps have a tinted background.
  Debloat opens on **Recommended**, with expandable filters and sorting. Selection actions wrap for longer translations and large fonts,
  with **Test first** as the primary action. A test checks access and backups; it cannot predict every
  effect on phone features.
- **Debloat lists** with about 5,400 packages, merged from three sources (the most cautious rating wins when
  they disagree):
  - Debloater's own list (473 packages with short descriptions, warnings and per-Android-version rules),
  - the [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)
    list (5,382 packages, detailed descriptions and dependencies; over 100 for Nubia/RedMagic and ZTE),
  - corrections from [System App Remover](https://github.com/santhosh-D-subramani/Debloater) (packages that are
    unsafe or usually cannot be uninstalled on their ROM),
  - the [Android Debloat List](https://github.com/MuntashirAkon/android-debloat-list) behind
    [adl.muntashir.dev](https://adl.muntashir.dev/) (4,801 packages with app names, warnings, links and
    open-source replacement suggestions).

  Tiers:
  - **Recommended**: ads, telemetry, sponsored installers and duplicates. Safe for almost everyone.
  - **Safe**: no effect on the system, but apps you might still use (YouTube, Maps, Gmail…).
  - **Extreme**: aggressive debloat. Every entry has a **warning** that says what breaks, and you must
    confirm that you read the warnings.
  - **Expert / Unsafe / Unlisted** (expert mode only): packages that can break important features, critical
    packages, and system apps not in any list.
  - Details for every package: full description, which installed apps need it ("needed by") and what it depends
    on. The confirm dialog warns when you remove something another installed app still needs.
  - If removal fails because a package cannot be uninstalled on your ROM, one tap retries the failed apps with
    Disable.
  - Some entries change level per Android version (for example Health Connect became a system module in Android 14).
- **Debloat by vendor**: the phone's brand is detected automatically (Redmi and POCO map to Xiaomi, RedMagic
  to Nubia, and so on). Filter the lists by vendor and select one vendor's apps in one tap: Recommended only,
  Recommended + Safe, or everything including Extreme. Packages that are not in the list are still grouped
  by vendor from their package name (for example `com.zte.*`, `cn.nubia.*`, `com.lge.*`), so in expert mode
  the Unlisted tab and the app manager can filter and remove any vendor's apps too.
- **Android Debloat List check**: scans the phone against [adl.muntashir.dev](https://adl.muntashir.dev/) and shows
  which listed apps are installed, grouped by ADL's rating (Delete, Replace, Caution, Unsafe), with a link to
  each app's page on the site and its suggested open-source replacements. You can select and test/disable/remove
  them from there.
- **Update lists in the app**: Settings → Debloat lists → *Update lists* downloads the newest UAD-NG, Android
  Debloat List and System App Remover data (about 3 MB) straight from their GitHub repositories, after asking
  first. The new files are loaded and checked before they replace the current lists; a source that fails keeps
  its previous version, and *Use built-in* goes back to the lists inside the APK. Optional weekly automatic
  check. Debloater's own list ships with APK releases; it does not require access to this private repository.
  Settings links to the UAD file and all five ADL files. GitHub file-page links are converted to raw JSON
  downloads, with a GitHub Contents API fallback if the raw host fails. Errors identify the failing URL;
  failed optional suggestion downloads keep their previous copies. The internet permission is used for
  nothing else.
- **Test run**: simulates the whole process step by step without changing anything. It checks Shizuku access,
  that the shell can see each package, that every APK can be read and backed up, and that there is enough free
  space. It shows every command that *would* run and reports whether any errors would happen. After a passed
  test you can continue with the real run.
- **Disable**: `pm disable-user`. Packages stay installed and can be re-enabled any time.
- **Remove**: you choose **Keep app data** (default, `pm uninstall -k --user <id>`: apps come back with their
  data, but almost no space is freed) or **Delete app data** (`pm uninstall --user <id>`: frees space, data is
  gone for good). System apps can always be
  restored with `install-existing`. Deleted user apps are reinstalled from their APK backup.
- **Remove all**: select everything in a tab (or across tabs) and remove it in one run.
- **App manager**: every user and system app, with search, filters (user/system, enabled/disabled/removed),
  and per-app disable/remove/restore. Multi-select works too.
- **Sorting** on every list (Debloat, App manager, ADL check): name A→Z or Z→A (in the phone's language order),
  largest or smallest first, most or least data first. The choice is remembered.
- **Sizes and storage**:
  - every app shows its size (app + data + cache). Exact sizes need Usage access, which the app grants
    itself through Shizuku (`appops set … GET_USAGE_STATS allow`); otherwise only APK sizes are shown,
  - the home screen shows total, used and free storage, the space freed by Debloater, the space used by
    backups, and how much each tier and all user/system apps take,
  - before a run you see the estimated space freed and the space the backups need; after a run you see the
    space actually freed (measured around each command, so backups are not counted) and the free space left.
    Each app's freed space is saved in its backup record.
- **Backups at all times** (they cannot be turned off):
  - a full-device **baseline snapshot** the first time the app runs,
  - a **restore point** (state of every package) before each run,
  - the **previous state** of every changed package,
  - a **verified copy of every APK split** before anything is disabled or removed. If the backup of a package
    fails, that package is **not touched**.
  - Restore single apps, whole sessions, or **undo everything** back to the baseline. Removed system apps come
    back with `install-existing`, deleted user apps are reinstalled from the APK backup (`pm install-create/write/commit`).
  - Export/import all backups as a zip.
- **Protected packages** (System UI, Settings, telephony, package installer, permission controller, WebView,
  Shizuku, your current launcher and keyboard, …) can never be disabled or removed, even in expert mode.
- Logs of every run and test are saved and can be shared.

## App leftovers

Open **App leftovers** to find package-named folders left by uninstalled apps in primary `Android/data`, `Android/obb` and `Android/media` storage.
Shizuku verifies package ownership; installed, disabled and retained packages are protected. Review paths and sizes before moving selected folders to recovery.
Recovery is persistent and can restore folders without overwriting existing data. Moving does not free space; a separately confirmed permanent deletion does.
See [scope, recovery and limitations](docs/APP-LEFTOVERS.md). Unrecognized folders and private/OS storage are outside scope.

## Cache cleaner

Open **User cache** or **System cache** from the main dashboard and choose **Application type**, then tap **Scan selected app type**.
The **Application type** button in the scanner selects **User apps**, **System apps**, or **Both**.
The measured total, unavailable-size count, application list and clear targets follow that choice.
Scanning measures only the selected category with two bounded workers. Switching categories reuses available results; unmeasured apps remain listed with unknown sizes. Selections in the other category are retained but are
excluded from clearing until that category (or Both) is chosen.
Usage access is required for measurements; enable it through Shizuku or Android Settings, then scan again.
The scanner shows measured user-app, system-app and combined cache totals for the current Android user.
Shared UID caches are grouped and counted once per volume; unavailable measurements are excluded from totals.
Protected OS/recovery caches, other Android users and the read-only system partition are outside the scan.

Sort by largest/smallest cache or app name A–Z/Z–A, search app/package names, or show only measured caches above zero.
Select shown results or individual groups, then **Clear selected caches** and review the confirmation.
Filtering does not deselect hidden selections; the confirmation lists every targeted app.

Choose **Clearing method**: **Shizuku** or **Settings assistant (Accessibility)** for either user or system app caches.
The optional Settings assistant opens each selected app's Android settings and presses **Clear cache**, like manual cleaning.
It requires a permission disclosure, manual Accessibility enablement and a warning/target confirmation before every batch.
A floating **Cancel** button stops the assistant. Unrecognized screens or uncertain outcomes stop safely; manufacturer compatibility varies.
It does not need Shizuku. Without Usage Access you can select apps, but cache sizes remain unknown.
See [Settings assistant setup, limitations and validation](docs/SETTINGS-CACHE-ASSISTANT.md).

The Shizuku method requires a successful `pm help` capability check for `clear --cache-only`.
Every command keeps the `--cache-only` flag and current user scope. Unsupported Android versions are blocked,
and ROM permission failures appear in the cache log. App data and accounts remain intact.
Cache deletion is permanent and cannot be restored from debloat backups. Only targeted non-empty/unknown cache groups are refreshed afterwards;
running apps can rebuild cache immediately, so actual freed space may differ from the measured total.

Measurements appear progressively, with a completed/total count and the current app. Cancel returns partial results.
Each storage-stat caller waits up to eight seconds; an unresponsive Binder call keeps its worker slot until it returns,
so retries cannot accumulate unlimited background requests. Unknown sizes are excluded from totals.
Cache-only Shizuku subprocesses wait up to 15 seconds, rather than the general 180-second command limit.
A timeout stops the batch and reports an unknown outcome; Android may still finish an already-requested clear.
Cancel during clearing stops subsequent commands after the current one. Already-completed clears are not undone.
Known-empty caches are skipped. App Manager sizes affected by clearing fall back to APK-only until refreshed,
without automatically measuring every app again.

On-device performance and cache behavior remain untested; no numeric speed improvement is claimed.

## Requirements

- Android 11 (API 30) up to Android 17 (API 37). Newer versions run but show an "untested" warning.
- Shizuku, started with Wireless debugging (no PC needed on Android 11+) or with root, for debloating and App leftovers.
- The Settings cache assistant instead requires manually enabled Accessibility access. Usage Access is needed for cache measurements.

## Usage

1. Install and start Shizuku, then open Debloater and grant the Shizuku permission. The welcome guide and
   the setup steps on the home screen walk you through it.
2. New to debloating? Tap **Clean up my phone**: it selects only Recommended apps; tap **Test**, then **Disable**.
3. For more control open **Debloat**. Start with a few apps you do not use in **Recommended**.
4. Tap **Test first** to check the process without changing anything. Read each app's warnings too.
5. If the test passes, tap **Continue…** and choose **Disable** or **Remove**.
6. Use **Backups & restore** to undo package changes. Deleted app data and cache cannot be restored.

For a first attempt, prefer **Disable** for any tier. It keeps the app and its data and is easy to undo.

## Limitations

- App *data* cannot be copied without root. Choose "Keep app data" when removing apps you might want back with their data.
- Disabling and removing with "Keep app data" free (almost) no storage. Removing a system app frees its data and
  updates, but its original APK stays on the read-only system partition.
- Backups live in the app's external files folder and are deleted if Debloater is uninstalled, so export them.
- A factory reset brings back every system app removed with `--user`.

## Building

```bash
python3 tools/check_translations.py           # coverage and format arguments for every language
./gradlew testDebugUnitTest assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug
./gradlew assembleRelease                     # requires the permanent release signing credentials
```

GitHub Actions builds both APKs on every push (see the `debloater-apks` artifact).

To publish a release, bump `versionCode`/`versionName` in `app/build.gradle.kts`, add
`docs/RELEASE-<version>.md` and merge. Then either push a tag `v<version>` on that commit, or open
**Actions → Build APK → Run workflow**, pick the branch and tick *Publish a GitHub release*. CI checks the
version and release notes, builds and verifies the signed APK, creates the tag if needed, and publishes a
GitHub release with `Debloater-v<version>.apk` and the release notes as its text.

Release APKs use one permanent key stored in the repository's `ANDROID_KEYSTORE_BASE64` and
`ANDROID_KEYSTORE_PASSWORD` Actions secrets. CI checks the APK certificate against
`config/release-certificate.sha256` before uploading it. Never regenerate this key: future updates
must use the same signer. Keep the private keystore and password backed up outside GitHub too;
neither belongs in Git or in release assets. For local release builds, set `ANDROID_KEYSTORE_PATH`
to the backed-up PKCS12 keystore and `ANDROID_KEYSTORE_PASSWORD` to its password (alias `debloater`).
Missing credentials fail the release build rather than falling back to a temporary debug key.

Pull requests build and test only the debug APK, without receiving the release key. Debug APKs use
the separate `io.github.j0p3s.debloater.debug` application ID, so they can coexist with the release app.

Versions through 1.7.0 were signed with temporary CI debug keys that were not retained. Moving from
those APKs to the permanent signer requires one final uninstall/reinstall; export backups first.
Subsequent releases signed with the permanent key can update in place and retain app data.

See [localization guidance](docs/LOCALIZATION.md) for adding translations and checking language changes.

## License

GPL-3.0, see `LICENSE`. The app bundles GPL-3.0 data from UAD-NG and System App Remover and AGPL-3.0 data
from the Android Debloat List; see `NOTICE.md`. The app can update the lists itself (Settings →
Debloat lists); `tools/update_uad_list.sh` and `tools/update_adl_list.sh` update the copies built into the APK.

## Project layout

| Path | What it does |
| --- | --- |
| `shizuku/ShellService.kt` | Shizuku UserService: commands, APK sessions, file copy and validated leftovers actions |
| `cache/SettingsCacheService.kt`, `cache/SettingsCachePolicy.kt` | Opt-in Settings UI assistance and conservative control/identity checks |
| `leftovers/LeftoverStore.kt`, `ui/LeftoverController.kt` | Ownership checks, recovery moves, restore/delete and operation state |
| `shizuku/ShizukuManager.kt` | Shizuku status, permission, binding, command execution |
| `data/` | Package listing, debloat list loader, protected packages, settings |
| `backup/` | Restore points, APK backups, export/import |
| `ops/OperationEngine.kt` | Test runs, disable/remove/restore with verification and logging |
| `ui/` | Jetpack Compose screens |
| `assets/debloat_list.json` | Debloater's own list |
| `assets/uad_lists.json` | UAD-NG list (unmodified, GPL-3.0) |
| `assets/uad_overrides.json` | Overrides from System App Remover (GPL-3.0) |
| `assets/adl/` | Android Debloat List (unmodified, AGPL-3.0) |
