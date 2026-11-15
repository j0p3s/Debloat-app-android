# Debloater 1.12.9

A review of texts and layout; no change to how apps are disabled, removed, restored or cleaned.

- **Home:** every tool row now has a divider and its own icon (User cache, System cache and App leftovers no longer share the Debloat icon). The getting-started guide sits in a card like the other sections.
- **Cache scanner and App leftovers:** sections are spaced instead of stacked edge to edge, secondary text is smaller and muted, results appear in a highlighted card, and the "Only measured caches above zero" row is centred and tappable as a whole.
- **App leftovers labels:** the selection line no longer says "measured cache", and the log is titled "Logs" with a "Copy log" button instead of "Cache command log".
- **Lists:** the count line is vertically centred with the Sort, Select all and Clear buttons in Debloat, App manager and the Android Debloat List check.
- **Dark mode:** success, warning and error colours are lighter on dark backgrounds so they stay readable.
- **Cards:** result, test-mode, backup and Shizuku cards span the full width instead of shrinking to their text.
- **Settings:** the App updates card uses the same spacing and text sizes as the other cards; the "Lists updated" line no longer leaves a blank line.
- **Permissions:** the Shizuku status is shown in your language instead of an internal code such as `PERMISSION_REQUIRED`.
- **Help:** the privacy answer now also mentions the optional app-update check.
- **Fix:** the Android Debloat List check no longer crashes if a downloaded list contains a removal rating this version does not know.

Android 11+, versionCode 34, permanent signing identity unchanged. Layout changes were compiled and checked by lint and unit tests, not yet viewed on a device.
