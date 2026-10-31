# Debloater (Shizuku)

An Android app that debloats **any Android version from Android 11 to Android 17** without root, using
[Shizuku](https://shizuku.rikka.app/). It also works as a normal app manager, so you can remove any user app.

## Features

- **Debloat lists** (390 curated packages for Google/AOSP, Samsung, Xiaomi, OnePlus/OPPO/Realme, Motorola,
  Huawei, Facebook, Microsoft, Amazon, carriers and preinstalled partner apps):
  - **Recommended**: ads, telemetry, sponsored installers and duplicates. Safe for almost everyone.
  - **Safe**: no effect on the system, but apps you might still use (YouTube, Maps, Gmail…).
  - **Extreme**: aggressive debloat. Every entry has a **warning** that says what breaks, and you must
    confirm that you read the warnings.
  - **Unsafe / Unlisted** (expert mode only): critical packages and system apps not in the list.
  - Some entries change level per Android version (for example Health Connect became a system module in Android 14).
- **Test run**: simulates the whole process step by step without changing anything. It checks Shizuku access,
  that the shell can see each package, that every APK can be read and backed up, and that there is enough free
  space. It shows every command that *would* run and reports whether any errors would happen. After a passed
  test you can continue with the real run.
- **Disable**: `pm disable-user`. Packages stay installed and can be re-enabled any time.
- **Remove**: `pm uninstall -k --user <id>` for system apps (restorable with `install-existing`). For user apps
  you can keep the data (restorable instantly) or delete them completely.
- **Remove all**: select everything in a tab (or across tabs) and remove it in one run.
- **App manager**: every user and system app, with search, filters (user/system, enabled/disabled/removed),
  sort by size, and per-app disable/remove/restore. Multi-select works too.
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

- App *data* cannot be copied without root. Use "keep data" when removing user apps you might want back.
- Backups live in the app's external files folder and are deleted if Debloater is uninstalled, so export them.
- A factory reset brings back every system app removed with `--user`.

## Building

```bash
./gradlew testDebugUnitTest assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                     # minified, signed with the debug key for sideloading
```

GitHub Actions builds both APKs on every push (see the `debloater-apks` artifact).

## Project layout

| Path | What it does |
| --- | --- |
| `shizuku/ShellService.kt` | Shizuku UserService (runs as shell/root): commands, APK install sessions, file copy |
| `shizuku/ShizukuManager.kt` | Shizuku status, permission, binding, command execution |
| `data/` | Package listing, debloat list loader, protected packages, settings |
| `backup/` | Restore points, APK backups, export/import |
| `ops/OperationEngine.kt` | Test runs, disable/remove/restore with verification and logging |
| `ui/` | Jetpack Compose screens |
| `assets/debloat_list.json` | Curated debloat list |
