# Debloater 1.11.7

- Identify disabled Clear cache controls even when Android reports them as non-clickable.
- Check the enabled state of the control and its ancestors before any click.
- Log the app as skipped and continue to the next selected app when the cache control is disabled.
- Add regression tests for disabled buttons and enabled labels inside disabled parent controls.

Applies to user and system apps. Uses Android control state rather than appearance. Android 11+, versionCode 23, permanent signing identity unchanged. Full device validation of the updated APK remains pending.
