# Debloater 1.12.1

Fixes based on the first debug report (nubia RedMagic OS 11, Android 16, Portuguese):

- **Shizuku cache clearing:** `pm clear --cache-only` could time out after 15 s, which stopped the batch even though Android keeps deleting in the background. After a timeout, Debloater now re-checks the app's real cache size for up to 60 s. If the cache is gone (at most 5 % left), the app counts as cleared and the batch continues. Otherwise the batch stops as before.
- **Settings assistant diagnostics:** when the Storage row or Clear cache button is not found, the debug log now lists the visible Settings controls (id, text, clickable/disabled). Every stop, skip and timeout records its exact reason. This is needed to add selectors for this Settings layout.
- The debug log records a one-line summary of `pm help` instead of the full text.

Android 11+, versionCode 26, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
