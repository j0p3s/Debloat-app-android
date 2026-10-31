# Debloater (Shizuku)

An Android app that debloats **any Android version from Android 11 to Android 17** without root, using
[Shizuku](https://shizuku.rikka.app/). It also works as a normal app manager, so you can remove any user app.

## Features

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
- **Test run**: simulates the whole process step by step without changing anything. It checks Shizuku access,
  that the shell can see each package, that every APK can be read and backed up, and that there is enough free
  space. It shows every command that *would* run and reports whether any errors would happen. After a passed
  test you can continue with the real run.
- **Disable**: `pm disable-user`. Packages stay installed and can be re-enabled any time.
- **Remove**: `pm uninstall --user <id>`. With **keep data** (`-k`) apps come back instantly with their data
  but almost no space is freed. Without it the app data is deleted to free space. System apps can always be
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
  Shizuku, your current launcher and keyboard, …) can never be modified, even in expert mode.
- Logs of every run and test are saved and can be shared.

## Requirements

- Android 11 (API 30) up to Android 17 (API 37). Newer versions run but show an "untested" warning.
- Shizuku, started with Wireless debugging (no PC needed on Android 11+) or with root.

## Usage

1. Install and start Shizuku, then open Debloater and grant the Shizuku permission.
2. Open **Debloat**, pick a tab, select packages (or **Select all**).
3. Tap **Test** to see the whole process without changing anything.
4. If the test passes, tap **Continue…** and choose **Disable** or **Remove**.
5. Use **Backups & restore** to undo anything.

Prefer **Disable** for Extreme packages: it is the easiest change to undo.

## Limitations

- App *data* cannot be copied without root. Use "keep data" when removing apps you might want back with their data.
- Disabling and removing with "keep data" free (almost) no storage. Removing a system app frees its data and
  updates, but its original APK stays on the read-only system partition.
- Backups live in the app's external files folder and are deleted if Debloater is uninstalled, so export them.
- A factory reset brings back every system app removed with `--user`.

## Building

```bash
./gradlew testDebugUnitTest assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                     # minified, signed with the debug key for sideloading
```

GitHub Actions builds both APKs on every push (see the `debloater-apks` artifact).

## License

GPL-3.0, see `LICENSE`. The app bundles GPL-3.0 data from UAD-NG and System App Remover and AGPL-3.0 data
from the Android Debloat List; see `NOTICE.md`. Update the lists with `tools/update_uad_list.sh` and
`tools/update_adl_list.sh`.

## Project layout

| Path | What it does |
| --- | --- |
| `shizuku/ShellService.kt` | Shizuku UserService (runs as shell/root): commands, APK install sessions, file copy |
| `shizuku/ShizukuManager.kt` | Shizuku status, permission, binding, command execution |
| `data/` | Package listing, debloat list loader, protected packages, settings |
| `backup/` | Restore points, APK backups, export/import |
| `ops/OperationEngine.kt` | Test runs, disable/remove/restore with verification and logging |
| `ui/` | Jetpack Compose screens |
| `assets/debloat_list.json` | Debloater's own list |
| `assets/uad_lists.json` | UAD-NG list (unmodified, GPL-3.0) |
| `assets/uad_overrides.json` | Overrides from System App Remover (GPL-3.0) |
| `assets/adl/` | Android Debloat List (unmodified, AGPL-3.0) |
