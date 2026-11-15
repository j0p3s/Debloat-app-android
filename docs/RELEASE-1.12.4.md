# Debloater 1.12.4

- **Clearer Shizuku cache message:** on some phones Android silently ignores `pm clear --cache-only` from the shell user. The command never returns and the cache size stays the same to the byte. In that case Debloater now says Android probably ignored the request and suggests the Settings method, instead of only reporting that the batch stopped. Confirmed on RedMagic OS 11 (Android 16), where the Settings method clears the same cache.
- Nothing else changes: success is still decided as before, and the batch still stops after an unverified timeout.

Android 11+, versionCode 29, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
