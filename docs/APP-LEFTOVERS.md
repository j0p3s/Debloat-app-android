# App leftovers

Open **App leftovers** from the dashboard, connect Shizuku and tap **Scan app leftovers**. Review paths and sizes, select folders, then **Move to recovery**. No result is preselected. Search and largest-first/name sorting are available.

## Ownership and scope

- Checks package-named directories directly under the current Android user's primary `Android/data`, `Android/obb` and `Android/media` folders.
- Before scanning and each move, Shizuku queries `pm list packages -u --user all`. Installed apps, disabled apps, other-user owners and retained package records are protected. Failed or unrecognized inventory aborts cleanup.
- A missing package is evidence of a possible leftover, not proof its files are unwanted. Media may be personal. Review every path.
- Arbitrary app-name folders, Downloads/photos, private `/data`, OS files, secondary storage and other users' storage are outside scope. This is not SD Maid's full forensic database or root cleaner.
- Access failures and partial scans are shown. Symlinks, malformed paths and oversized/deep trees are rejected; work and result counts are bounded.

## Recovery and space

- Confirmed cleanup moves each selected directory to `.DebloaterRecovery/<record-id>/payload` on the same primary volume. `record.json` stores the original location before moving. No copy-then-delete fallback is used if the move fails.
- Files remain in shared storage; this is not encrypted backup. Moving to recovery does **not** free space. Records can be loaded after app/process restart.
- **Restore folder** requires confirmation and never merges or overwrites an existing folder. Pause installations, uninstallations and other file operations while using the tool.
- **Permanently delete** requires another warning and deletes only the chosen recovery payload. This frees space but is irreversible and removes the recovery copy. Partial deletion failures retain remaining files/metadata for review.
- Cancel stops between bounded privileged operations; the in-flight operation finishes first. Completed actions are logged and not automatically undone.
- If Android or the manufacturer prevents access/moves, failure is reported and source files remain intact. No destructive fallback is attempted.

## Verification

- Tests cover installed/disabled/retained owners, inventory failure, reinstall rechecks, persistent recovery, restore conflicts, selected-record deletion, invalid metadata/path traversal and symlink escape prevention.
- Before release, test on a phone with disposable folders, retained/uninstalled and system packages, permission denial, stop, app restart, conflicting restore and deletion of one recovery record. CI cannot verify Android FUSE permissions or manufacturer compatibility.

Independently follows the ownership-checking approach of [SD Maid SE CorpseFinder](https://github.com/d4rken-org/sdmaid-se/tree/main/app-tool-corpsefinder). No source, fingerprints, translations or assets were copied.
