# Languages and usability

The interface uses Android string resources, with English as the fallback and complete Spanish,
French, German, Portuguese, Italian, Russian, Simplified Chinese (`values-zh-rCN`), Hindi, Arabic and
Indonesian (`values-in`, declared as both `id` and `in`) resource sets. The Portuguese, Italian, Russian,
Chinese, Hindi, Arabic and Indonesian translations and the beginner-guide strings (`guide_*`) are
machine-assisted and need native speaker review. Android selects the device language initially. Settings offers
language autonyms so users can recover from choosing a language they cannot read.

`AppCompatDelegate.setApplicationLocales` changes the language and recreates the activity.
AppCompat stores the preference on Android 11–12 and integrates with Android 13+ per-app language
settings. The activity ViewModel retains selections and active operations during recreation.
Saved enum names, package names, backup JSON and shell commands remain language independent.

Descriptions and warnings supplied by the debloat databases, technical command output and stored
logs retain their source language. Settings explains this limitation. Translated safety guidance
does not replace reading a package's source warning. Native speaker review is welcome.

## Add a language

1. Copy the translatable entries from `app/src/main/res/values/strings.xml` into the appropriate
   Android locale directory. Leave brand names and the license notice in the default resources.
2. Translate whole sentences. Preserve numbered placeholders such as `%1$d` and `%2$s`; they may
   move within the sentence. Use Android plurals for new sentences that require plural inflection.
   Existing count summaries use neutral labels such as `Selected: %1$d`.
3. Add the language tag to `res/xml/locales_config.xml` and its autonym to `LanguageSetting.kt`.
   Regional tags need Android's resource qualifier syntax; adapt the coverage check accordingly.
4. Run `python3 tools/check_translations.py`, `./gradlew testDebugUnitTest assembleDebug`, and
   `./gradlew lintDebug`. The coverage check also runs in GitHub Actions.

## Device verification

- Check every screen in each supported language, at a narrow width and with large system fonts.
  Selection actions and settings buttons should wrap, and confirmation text should scroll.
- On Android 11 or 12, select a language, rotate, close and reopen the app, and restart the process.
  Confirm the language is retained. Return to “Use device language” and check it follows the device.
- On Android 13+, change the language in both the app and system per-app settings. Confirm they agree.
- Use an unsupported device language to check English fallback.
- Keep an app selection and rotate. Change language from Settings and return to the list. Confirm
  selections and existing backups remain intact. Operation progress should survive recreation and
  must not start a second operation.
- Review disable/remove confirmations and Keep/Delete app data choices in every language. The
  primary test action should default to Disable, and tests must not change device state.

The UI retains the existing Material theme. The changes focus on action placement, a short getting
started guide, an initial Recommended filter, readable wrapping actions, and explicit test limits.
