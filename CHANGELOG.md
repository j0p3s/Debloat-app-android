# Changelog

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
