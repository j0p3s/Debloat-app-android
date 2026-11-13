# Debloater 1.11.3

- Fix stale failed-download errors when retrying update checks.
- Fall back to the official public latest-release page when the GitHub API fails. Only valid stable version tags from this repository are accepted.
- Show actual HTTP/Android download errors and a direct GitHub release-page link.
- Add redirect validation regression tests.

Android 11+, versionCode 19, permanent signing identity unchanged. The exact previous phone-side failure remains unconfirmed; physical-device testing is pending.
