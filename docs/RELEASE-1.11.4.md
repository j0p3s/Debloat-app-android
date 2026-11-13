# Debloater 1.11.4

- Fix the full App info → Storage and cache → Clear cache path on identifiable OEM Settings screens, including missing standard header IDs and wrapped Storage labels.
- Read the installed Settings resources and add language-specific fallbacks for English, Portuguese, Spanish, French, German, Italian, Russian, Simplified Chinese, Hindi, Arabic and Indonesian. Follow the Android Settings language, independently of Debloater's interface language.
- Add two-screen selector regression tests for every supported language, preserving exact cache-vs-data separation and unique app identity checks.

- Continue Accessibility cache cleaning after an app has no safely identifiable cache control, provided no cache button was pressed. Record the skip in the log.
- Recognize additional Settings Storage rows and tolerate short fragment transitions without clicking during them.
- Stop on uncertain post-click results, screen lock or leaving Settings. No blind clicks or data clearing.
- Add regression tests for safe continuation and custom Settings rows.

The previous return to Debloater could be caused by the intentional batch-stop path; an actual phone crash has not been confirmed without logs. Physical-device validation remains pending.

The official GitHub repository is now public, resolving unauthenticated OTA HTTP 404 responses. API, release page and APK returned HTTP 200 without credentials.

Android 11+, versionCode 20, permanent signing key unchanged.
