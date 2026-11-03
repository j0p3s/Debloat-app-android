# Debloater 1.10.0

## More languages
- New: Portuguese, Italian, Russian, Simplified Chinese, Hindi, Arabic (right-to-left) and Indonesian.
  With English, Spanish, French and German the app now speaks 11 languages.
- Every screen is translated, including the cache tools from 1.9.0. The app follows the device language;
  pick another in **Settings → Language**, on the welcome screen, or in Android 13+ per-app language settings.
- The new translations are machine-assisted. Corrections from native speakers are welcome
  (see `docs/LOCALIZATION.md`).

## Easier for beginners
- **Welcome guide** on first launch: choose a language, learn the test → disable → undo routine, and
  connect Shizuku. Show it again any time from Help.
- **Shizuku setup steps**: until Shizuku works, the home screen shows four numbered steps (install,
  Wireless debugging, start, allow) with a button for each.
- **Clean up my phone**: the first entry on the home screen. It lists the Recommended apps found on this
  phone, already selected, with **Test** and **Disable** as the main actions. Disabled apps can be turned
  back on from Backups & restore. Uninstalling is still available as a secondary choice.
- **Help**: the question-mark icon in the top bar answers common questions in plain language.

Debloat lists, vendor filters, the app manager, cache tools and backups work as before.

## Installation
Download **Debloater-v1.10.0.apk**. Requires Android 11 or later and Shizuku.

This release keeps application ID `io.github.j0p3s.debloater` and the permanent signer introduced in 1.8.0,
so it installs over 1.8.0 or 1.9.0 and keeps your data and backups. Versions through 1.7.0 used temporary
keys: export backups before the one-time uninstall/reinstall.

## Known limits
- The new screens and languages have not yet been checked on a physical device.
- Package descriptions and warnings from the debloat databases, and technical logs, stay in English.

## Release metadata
- Version: **1.10.0**
- Version code: **12**
- Signing: permanent release certificate, verified by CI against `config/release-certificate.sha256`.
- Validation: translation coverage (374 strings × 11 languages), 57 unit tests, debug build, lint, release
  signing guard, signed release build and certificate check.
