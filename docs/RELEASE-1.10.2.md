# Debloater 1.10.2

## Cache application-type selector
- The cache scanner now has an Application type button with User apps, System apps and Both.
- Cache totals, unavailable-size counts, application lists and clearing targets follow the chosen type.
- Switching types reuses the current scan. Choosing Both shows user and system caches in one list with their separate totals.
- Select shown and Deselect apply to the current type. Selections in other types are retained but are excluded from the clear confirmation until their type (or Both) is chosen.
- The choice survives screen rotation. Existing search, size filters and sorting continue to apply.
- New labels are translated into all 11 supported languages.

## Compatibility
Version 1.10.2, version code 14. Same application ID and permanent signing identity as 1.10.1. Android 11 or later. Package operations require Shizuku; measurements require Usage access.

## Verification
Translation coverage, unit tests (including category totals and clear-target isolation), debug build, Android lint, release signing guard, signed release build and certificate verification passed in CI. On-device visual checks remain pending.

## Download
Install **Debloater-v1.10.2.apk** over the current Debloater app. App data and backups are retained. The same permanent signing certificate is used.

- [Verified signed build](https://github.com/j0p3s/Debloat-app-android/actions/runs/41769967281).
- [Pull-request checks](https://github.com/j0p3s/Debloat-app-android/actions/runs/41769994180).
- Certificate SHA-256: `50b9cebb9f7ec6df5b0d38b5dd18bf7ccb7a124950106ff9187dfa23a64c9fb2`.
- APK hashes are included in the attached **SHA256SUMS.txt**.
- [Full changelog](https://github.com/j0p3s/Debloat-app-android/blob/v1.10.2/CHANGELOG.md).
