# Changelog

## 1.10.3 � 2026-11-04

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
