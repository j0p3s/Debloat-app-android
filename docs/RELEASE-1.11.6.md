# Debloater 1.11.6

- Recognize both “Storage and cache” and “Storage & cache”; the former was observed directly on a Nubia Android 16 Settings screen. Existing supported-language resources/fallbacks remain available.
- Add a selector regression test with the observed Settings row identifiers and clickable parent hierarchy.
- Move the floating Cancel bar to the bottom so it does not cover the Settings title.
- Add identity, selector and exception details to the cache log.
- Fix unhandled InterruptedIOException in Shizuku command-output reader threads, observed in the supplied/device crash logs. Add interrupted-reader regression coverage.

The supplied logs show an older privileged-process crash and recent app-return/service-rebinding events; they do not establish a new main-process crash during the reported screens. The new diagnostic log is intended to identify remaining phone-specific failures. No cache or app data was cleared during read-only Settings inspection; end-to-end cleaning with this APK remains to be verified on the phone.

Includes 1.11.5 Accessibility default and confirmed Shizuku permission setup. Android 11+, versionCode 22, permanent signing identity unchanged.
