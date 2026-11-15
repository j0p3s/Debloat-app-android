# Debloater 1.11.5

The cache cleaner now defaults to Settings assistant (Accessibility) for both user and system apps. Shizuku remains selectable as an alternative.

Accessibility must still be explicitly enabled and each cleaning batch requires confirmation. Choosing the default does not enable permissions or start cleaning automatically.

Android 11+, versionCode 21, existing permanent signing identity. Physical-device testing remains pending.

Adds Permissions → Set up access with Shizuku. After a disclosure and confirmation, it grants Usage Access and attempts to enable only the cache assistant, preserving other Accessibility services. Each result is checked; manual setup remains available if Android blocks activation. Setup does not start cleaning or grant Shizuku authorization to itself. New guidance covers all supported languages.
