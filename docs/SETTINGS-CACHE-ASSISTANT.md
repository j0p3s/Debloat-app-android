# Settings cache assistant

- In **User cache** or **System cache**, choose **User apps**, **System apps**, or **Both**, load the app list, and select targets.
- Choose **Clearing method → Settings assistant (Accessibility)**. Read the disclosure before opening Android Accessibility settings and enable **Settings assistant** yourself.
- Return to Debloater and tap **Clear selected caches**. Review the target list and warning; nothing starts until you confirm. Granting Accessibility access never starts a batch.
- Keep the screen unlocked and avoid touching Settings. The assistant opens each selected app's details, enters Storage, and presses only Clear cache. The floating **Cancel** button stops further actions immediately; earlier clears are permanent.
- This method does not require Shizuku. Usage Access is still needed to measure cache sizes, but app inventory remains selectable without it. Unknown sizes are never treated as zero.
- Both user and system app selections use the same scope rules. Android can restrict system apps; Settings itself and Debloater are skipped to keep the assistant running.
- Only a system-installed `com.android.settings` handler is used. The current app identity, exact localized Settings labels and recognized cache control IDs must all match. Duplicate app labels require a visible matching package name.
- No coordinate taps, blind keys, data clearing, dialog confirmations or automatic method switches are used. Unsupported manufacturer layouts and unverified outcomes stop the batch after at most 20 seconds per app.
- A confirmed UI press means Settings disabled Clear cache afterwards; it is not proof of reclaimed bytes. Targeted measurements check sizes when Usage Access is available.
- The service reads Settings controls only during an explicitly confirmed run. Screen contents are not stored or transmitted. Disable the assistant in Accessibility settings whenever desired.
- Independently implements the user-like Accessibility approach used by [SD Maid SE AppCleaner](https://github.com/d4rken-org/sdmaid-se/tree/main/app-tool-appcleaner). No source, translations or assets were copied. Its broader manufacturer support is not implied.

## Validation before release

- Unit tests cover cache vs data controls, localized labels, identity, duplicate labels, dialogs, disabled/hidden controls and unsupported layouts.
- Test one disposable user app, then a permitted system app, all scopes, cancellation, lock screen, revoked Accessibility, empty cache and an unsupported Settings page on a real phone.
- Check app data/accounts remain intact, granting access never starts cleaning, process death never resumes actions and unknown outcomes never appear as success.
- Device/OEM compatibility and performance require physical-device testing; CI does not establish these.

## Missing controls

Before treating a cache control as unavailable, the assistant follows App info → Storage and cache → Clear cache. OEM screens may omit standard header/control IDs: a recognized Settings page title plus the exact unique installed app label can identify the screen. Wrapped action text is normalized. Language-specific fallbacks cover all 11 app languages; installed Settings resources remain the primary label source. Device/ROM translation variants still require phone validation.

If no cache button was pressed and Settings remains open, an unavailable control is logged and skipped. The batch continues with the next app. Short fragment transitions are tolerated without clicks. Uncertain outcomes after a cache press, screen lock or leaving Settings still stop the sequence. This behavior applies to user and system apps.
