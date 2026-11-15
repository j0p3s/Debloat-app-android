# Debloater 1.12.6

- **Cache that Clear cache cannot remove:** on some apps (seen with Clash of Clans 2.2 GB and SmartThings 341 MB on RedMagic OS 11), Android keeps reporting the same cache size after a confirmed clear. Android counts these files as cache although they are not in a cache folder, so Clear cache, Debloater and SD Maid cannot remove them. An app that refills its cache at once looks the same. Debloater now remembers when a confirmed clear left 8 MB or more and at least half of the cache. It marks the app with the leftover size, explains that usually only clearing the app's storage or reinstalling frees it, and shows the total of such leftovers.
- **Measured with Shizuku:** for these apps, Debloater also measures the real content of `Android/data/<app>/cache` on shared storage and shows it, so a near-empty folder confirms the reported size is not real removable cache. The internal cache folder cannot be read without root.
- The mark disappears when the app gains new, clearable cache or a later clear frees it.

Android 11+, versionCode 31, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
