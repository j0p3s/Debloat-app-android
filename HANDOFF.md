# Handoff — Debloater (Android)

## 1. Goal
- A beginner-friendly Android 11–17 debloater (Kotlin/Compose, Shizuku). It has safe/recommended/extreme lists, test mode, disable and uninstall, always-on backups, and an app manager. Release APKs are signed in CI with a permanent key.

## 2. Current status
- **Latest version is 1.10.3** (versionCode 15). It was merged into `ccr-d8c5609f-bq6ov9` via PR #6.
- Each release is listed in `CHANGELOG.md`, with full notes in `docs/RELEASE-1.10.{0,1,2,3}.md`:
  - 1.10.0: 7 new languages (11 in total, Arabic is RTL), the welcome guide, Shizuku setup steps, "Clean up my phone" and Help.
  - 1.10.1: footer fix so buttons no longer overlap the navigation bar.
  - 1.10.2: user/system/both app-type filter in the cache scanner.
  - 1.10.3: cache scans that are bounded, cancellable and responsive.
- Nothing is in progress. Before assuming the v1.10.3 GitHub release exists, confirm it with `get_release_by_tag v1.10.3`.
- Nothing has been checked on a real device yet.

## 3. Key decisions (don't re-debate)
- **License is GPL-3.0**, because the app bundles the UAD-NG, System App Remover and ADL lists. See `NOTICE.md`.
- **Release signing happens only in CI.**
  - The key comes from the secrets `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD`.
  - The certificate is pinned in `config/release-certificate.sha256`.
  - Never give the user a locally signed APK.
- The app ID `io.github.j0p3s.debloater` and the signer are unchanged since 1.8.0, so updates install in place. Debug builds use the `.debug` suffix.
- **Releases are published by CI** with softprops/action-gh-release and GITHUB_TOKEN. Two ways to trigger it:
  - Push a `v<version>` tag.
  - Use Actions → Build APK → Run workflow and tick `publish_release`.
- CI requires `docs/RELEASE-<versionName>.md` to exist; its contents become the release notes.
- Disable/Test is the main action for beginners and uninstall is secondary. Backups are always on.
- Translations are machine-assisted, and database descriptions stay in English.

## 4. Tried and failed (don't retry)
- `git push` of tags gets HTTP 403 from the session's git proxy. Use the workflow_dispatch input instead, via the MCP tool `actions_run_trigger`.
- The `gh` CLI token is invalid. Use the `mcp__github__*` tools.
- The GitHub MCP tools have no create-release tool, which is why CI publishes releases.
- Merging a draft PR through the API returns 405, and un-drafting through the API failed (GraphQL error). Instead, merge locally with `git merge --no-ff` into the dev branch and push; GitHub then marks the PR as merged.

## 5. Files that matter
- `app/build.gradle.kts:23-24` holds versionCode/versionName. Bump both for every release.
- `CHANGELOG.md` gets a new entry for every release.
- `.github/workflows/build.yml` is the CI pipeline:
  - Release check: lines 8 and 22.
  - Steps: translations, tests, lint, signed build and signer check.
  - Publish: lines 105–115.
- `docs/RELEASE-<ver>.md` holds the release notes that CI publishes. `README.md` has the "Current release" header and the release how-to under Building.
- `app/src/main/java/io/github/j0p3s/debloater/`:
  - `shizuku/`: the Shizuku UserService and AIDL shell.
  - `ops/OperationEngine.kt`: disable, uninstall and restore.
  - `backup/`: always-on backups.
  - `data/`: lists, vendor catalog, protected packages, list updater, prefs and sorting.
  - `ui/`: Compose screens, the ViewModel and the guide/help screens.
- `app/src/main/assets/` holds the bundled lists: `debloat_list.json`, `uad_lists.json`, `uad_overrides.json` and `adl/`. Refresh them with `tools/update_uad_list.sh` and `tools/update_adl_list.sh`.
- `app/src/main/res/values*/strings.xml` holds 374 strings × 11 languages. Run `python3 tools/check_translations.py` after any string change. Also update `res/xml/locales_config.xml` when adding a locale.
- `docs/LOCALIZATION.md` is the translation guide.

## 6. Next steps
1. Run `git pull origin ccr-d8c5609f-bq6ov9`, then confirm the v1.10.3 release exists with `get_release_by_tag`. Other sessions also push to this branch.
2. Ask the user for on-device results. Focus on the guide, the Shizuku steps, Clean up my phone, the cache scanner and Arabic RTL. Fix anything they report.
3. To make the next release:
   - Bump `app/build.gradle.kts`, add `docs/RELEASE-<ver>.md`, and add a `CHANGELOG.md` entry.
   - Update the README header links and push to `ccr-d8c5609f-bq6ov9`.
   - Trigger "Build APK" with `publish_release=true`.
   - Check the result with `get_release_by_tag`.
4. Ask the user before doing either of these: delete the old untagged "Debloater v1.9.0" **draft** release, or bring `main` up to date (it is still at 18e6e27).

## 7. Open questions / gotchas
- `main` is stale. The real line of work is `ccr-d8c5609f-bq6ov9`. Push only there, or to feature branches with PRs. Never force-push shared branches.
- Local builds need the Maven mirror init script `~/.gradle/init.d/mirror.gradle` and the SDK at `/opt/android-sdk`. Both live in the container and will not exist in a fresh one.
- `dist/Debloater-1.7.0.apk` is untracked and old. Don't distribute it.
- The local branch `local/i18n-beginner-redesign` is a leftover and is already merged.
- Don't put model identifiers in commits or PRs.
