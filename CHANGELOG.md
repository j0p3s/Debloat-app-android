# Changelog

## 1.12.5 — 2026-11-05

- Shizuku cache clearing: after a run, a dialog lists the apps Shizuku could not clear. If Android ignored Shizuku entirely, it also lists the apps that were not tried. Tick the apps to clear with the Settings method instead; the usual Settings assistant confirmation follows. Nothing switches methods without this choice.
- When the cache size has not changed by a single byte for 20 s after a timed-out shell clear, stop waiting early (previously 60 s).


## 1.12.4 — 2026-11-05

- Shizuku cache clearing: when the command times out and the cache size has not changed by a single byte, say that Android probably ignored the request and suggest the Settings method, instead of only "stopped". Seen on RedMagic OS 11 (Android 16), where the Settings method works.


## 1.12.3 — 2026-11-05

- Cache cleaner debug log: record each app's cache size before and right after clearing, for both the Shizuku and the Settings method. Diagnostic only; success is decided exactly as in 1.12.2, because an open app can refill its cache at once.


## 1.12.2 — 2026-11-05

- Settings assistant: read Settings labels in the **phone's** language, not Debloater's own language. With a different in-app language (e.g. Portuguese app on an English phone) the Storage row and Clear cache button were never found.
- Shizuku cache clearing: after a `pm clear --cache-only` timeout, a residue of at most 1 MiB (empty cache folders) counts as cleared; log the cache size before each clear.


## 1.12.1 — 2026-11-05

- Shizuku cache clearing: when `pm clear --cache-only` times out (seen on RedMagic OS 11 / Android 16), re-check the app's real cache size for up to 60 s. Android keeps deleting after the command is stopped; a cleared cache now counts as success and the batch continues.
- Settings assistant debug log: list the visible Settings controls when the Storage row or Clear cache button is not found, and log the exact reason for every stop, skip and timeout.
- Debug log: log only a one-line summary of `pm help` output.


## 1.12.0 — 2026-11-05

- New **Settings → Debug** section: a persistent on-device debug log of every tool (debloat/restore operations, cache cleaner and Settings assistant, leftovers, permission setup), every Shizuku command with exit code and output, user-visible messages, list-update results, caught errors and crashes.
- Save a full report (device info, debug log, saved operation logs, app logcat) into a folder of your choice, or export/share it. Clear the log at any time. Nothing leaves the phone unless you save or share it.
- Includes the unreleased 1.11.8 cache-assistant fix (wait up to 2 minutes for large caches).


## 1.11.8 — 2026-11-05

- Settings assistant: after pressing Clear cache, wait up to 2 minutes for Android to finish deleting instead of timing out within the 20-second search window. Large caches (e.g. 2+ GB game caches) previously timed out before Settings disabled the button.
- Log the press and wait in the cache log.


## 1.11.7 — 2026-11-05

- Recognize disabled cache controls even when they are non-clickable.
- Check ancestor enabled state before clicking, log a skip and continue to the next app.
- Add disabled-control and disabled-parent regression tests.


## 1.11.6 — 2026-11-05

- Add the actual Nubia English “Storage and cache” label alongside “Storage & cache”.
- Add a regression test using the observed Settings row identifiers and parent hierarchy.
- Move the floating Cancel bar to the bottom and log cache navigation/identity/exception diagnostics.
- Contain interrupted command-pipe reader exceptions so they do not crash the Shizuku service.
- Add worker-thread regression tests for interrupted output reads.


## 1.11.5 — 2026-11-05

- Add confirmed Shizuku-assisted permission setup, preserving existing Accessibility services and validating results.
- Translate setup guidance across supported locales and test safe service-list merging.

- Default cache cleaning to Settings assistant (Accessibility), for user and system apps.
- Keep Shizuku selectable, with existing consent and permission checks unchanged.


## 1.11.4 — 2026-11-05

- Fix OEM App info → Storage and cache → Clear cache recognition with verified target identity, including id-less headers and wrapped labels.
- Add Settings-language fallbacks and two-screen safety tests across all 11 supported app languages.

- Skip unavailable Accessibility cache controls before any cache click, rather than terminating the entire batch.
- Recognize additional Settings Storage row IDs and allow fragment transitions to settle.
- Keep stopping for uncertain post-click outcomes, lock and leaving Settings.
- Add regression tests for missing controls and custom Settings rows.
- Confirm unauthenticated GitHub update access after the repository became public.


## 1.11.3 — 2026-11-05

- Fix persistent updater errors from old failed downloads.
- Add a validated official GitHub latest-release redirect fallback for API failures.
- Display HTTP/download error details and a direct GitHub release link.
- Add regression tests rejecting foreign repositories and prerelease fallback tags.


## 1.11.2 — 2026-11-05

- Add on-demand GitHub app update checks, APK download progress/cancellation and Android-managed downloads.
- Keep installation user-confirmed; add numeric version comparison tests.


## 1.11.1 — 2026-11-05

- Add Settings → Permissions with live access validation and setup links.
- Show processed/total and remaining apps in the Accessibility cache overlay; remaining includes the current app.
- Count processed attempts, including skipped apps, without implying successful deletion.
- Translate the counter across supported locales; new permissions labels currently use English outside Portuguese.


## 1.11.0 — 2026-11-05

- Add an opt-in Settings Accessibility cache assistant for selected user and system apps, with disclosure and a warning before every batch.
- Verify app identity and localized cache controls; stop on unknown screens, lock, cancellation or uncertain outcomes. Keep Shizuku as a separate method.
- Allow selecting cache apps without Usage Access; measurement still requires it.
- Add App leftovers: review possible orphan package folders in current-user primary Android/data, Android/obb and Android/media storage.
- Protect installed, disabled and retained packages; reject unknown owners, unsafe paths and symbolic links.
- Move reviewed leftovers into persistent recovery first; restore without overwrites, or permanently delete individual recovery records after a separate warning.
- Add regression tests, setup/limitation docs and translations for all supported languages.


## 1.10.3 — 2026-11-04

- Measure only the selected cache category using two bounded workers and progressive partial results.
- Add cancellable measurements with eight-second caller waits and bounded outstanding Binder calls.
- Use a 15-second subprocess wait for cache-only Shizuku commands; stop on unknown timeout outcomes.
- Show scan/clear/refresh phases, current app and completed counts; allow stopping the operation.
- Skip known-empty caches and refresh targeted groups only after clearing.
- Remove the hidden full-device App Manager size reload after clearing; affected sizes fall back to APK-only until refreshed.
- Keep unmeasured app categories visible as unknown, rather than treating them as empty.
- Add timeout/cancellation tests and translate the new controls into all supported languages.

## 1.10.2 — 2026-11-04

- Added an **Application type** selector to the cache scanner: **User apps**, **System apps**, or **Both**.
- The measured cache total, unavailable-size count, app list, select/deselect actions and clearing targets now follow the selected type.
- Switching types reuses the current scan; **Both** shows a combined list and separate user/system totals.
- Selections in other categories remain saved but are excluded from clearing until their category (or Both) is selected.
- The type choice survives rotation, and works with search, cache-size filters and sorting.
- Translated the new controls into all 11 supported languages.
- Added regression tests for category totals and excluding selected system apps from user-only clearing.

## 1.10.1 — 2026-11-04

- Fixed welcome-guide footer buttons overlapping Android's navigation bar.
- Aligned the guide's actions in equal-width areas so longer labels wrap inside their buttons.
- Reserved navigation-bar space in the **Clean up my phone** footer.

## 1.10.0 — 2026-11-03

- Added Portuguese, Italian, Russian, Simplified Chinese, Hindi, Arabic and Indonesian translations.
- Added the welcome guide, Shizuku setup steps, **Clean up my phone**, and Help.
- Retained the cache tools, debloat lists, backups and permanent release signer.

Detailed notes: [1.10.3](docs/RELEASE-1.10.3.md), [1.10.2](docs/RELEASE-1.10.2.md), [1.10.1](docs/RELEASE-1.10.1.md), [1.10.0](docs/RELEASE-1.10.0.md).
