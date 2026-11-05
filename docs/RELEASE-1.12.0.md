# Debloater 1.12.0

## Debug logs
- New **Settings → Debug** section. Debloater keeps a size-limited debug log on the phone with:
  - logs of every tool: debloat, test runs and restore, cache cleaner (Shizuku and Settings assistant), app leftovers and permission setup;
  - every Shizuku command with its exit code and (truncated) output;
  - user-visible messages and tool notices, list-update results, caught errors and crash stack traces.
- **Save to folder** writes a timestamped report into a folder you pick once (remembered). **Choose folder** changes it. **Export / share** opens the Android share sheet. **Clear debug log** deletes it.
- A report contains device and app details, Shizuku/Usage Access/assistant state, the debug log, the 20 newest operation logs and the app's own logcat.
- Nothing is uploaded or sent automatically. Reports include package names and device details, so check them before sharing publicly.

## Cache assistant (from unreleased 1.11.8)
- After its single Clear cache press, the Settings assistant now waits up to 2 minutes for Android to finish deleting. Before, the whole per-app run had to fit in 20 seconds, so very large caches (such as a 2.2 GB game cache) could time out.

Android 11+, versionCode 25, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
