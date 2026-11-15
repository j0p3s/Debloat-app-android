# Debloater 1.12.5

- **Settings method fallback for Shizuku cache clearing:** when Shizuku cannot clear some apps, a dialog lists them, with checkboxes and sizes, after the run. If Android ignored Shizuku entirely (the cache did not change by a single byte), the apps that were not tried yet are listed too. Choose **Use Settings method** to clear the ticked apps through Android Settings instead. The usual Settings assistant warning and confirmation follow, and the assistant must already be enabled. Nothing switches methods on its own.
- After a timed-out shell clear, Debloater stops waiting after 20 s if the cache size has not changed at all (previously 60 s).
- Seen on RedMagic OS 11 (Android 16): `pm clear --cache-only` from Shizuku is ignored, while the Settings method clears the same cache.

Android 11+, versionCode 30, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
