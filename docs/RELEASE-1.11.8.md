# Debloater 1.11.8

- Settings cache assistant: after its single Clear cache press, wait up to 2 minutes for Android to finish deleting before giving up. Previously the whole per-app run, including that wait, had to fit in 20 seconds. Very large caches, such as a 2.2 GB game cache, could time out before Settings disabled the button, which stopped the batch.
- Finding App info, Storage and the cache control still has to finish within 20 seconds. Cancel stays available during the wait.
- Log the press and the wait in the cache log.

Applies to user and system apps. Android 11+, versionCode 24, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
