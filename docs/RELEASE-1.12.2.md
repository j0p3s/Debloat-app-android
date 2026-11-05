# Debloater 1.12.2

Fixes from the second debug report (nubia RedMagic OS 11, Android 16):

- **Settings assistant language:** Settings labels are now read in the phone's system language. When Debloater used its own language setting (for example Portuguese while the phone is English), Android reported Debloater's language. The assistant then searched for "Armazenamento e cache" on an English "Storage and cache" screen and never found the Storage row or Clear cache button.
- **Shizuku cache clearing:** on this ROM, `pm clear --cache-only` does not return even for small caches. After the 15 s timeout, Debloater re-checks the real cache size. A residue of at most 1 MiB (empty cache folders) now also counts as cleared and the batch continues. The cache size before each clear is logged.

Android 11+, versionCode 27, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
