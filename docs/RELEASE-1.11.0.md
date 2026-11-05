# Debloater 1.11.0

Release date: 2026-11-05 · versionCode 16 · Android 11+

- Optional Settings cache assistant uses Android Accessibility to navigate to Storage and press Clear cache for selected user/system apps.
- Requires disclosure, manual service enablement and a per-batch warning. Shows a floating Cancel button, verifies identities/controls and stops on uncertain screens/results.
- Adds App leftovers with ownership checks, reviewed selections, persistent recovery, non-overwriting restore and separately confirmed permanent deletion.
- Updates supported languages, README, changelog, notices and tool setup/limitation guides.

Usage Access measures cache sizes; Shizuku remains available for cache-only commands and is required for leftovers. Shared recovery does not free space until permanently deleted. Cache clearing and permanent deletion cannot be undone.

Settings-layout coverage and leftovers scope are conservative, rather than SD Maid's full manufacturer/forensic coverage. Physical-device compatibility and performance testing remain necessary.

The release workflow validates translations, runs unit tests, builds the APKs, runs Android lint, checks that unsigned release builds are refused and verifies the pinned permanent signing certificate before publishing.
