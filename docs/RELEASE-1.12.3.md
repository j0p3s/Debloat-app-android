# Debloater 1.12.3

- **Cache cleaner diagnostics:** the debug log now records each app's cache size before and right after clearing, for both the Shizuku and the Settings method. This shows whether a clear really deleted anything on a given phone.
- The size is for diagnosis only and does not change how success is decided. An app that is open, or opened again, can rebuild its cache immediately. For a clean test, force-stop the app before clearing.

Android 11+, versionCode 28, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
