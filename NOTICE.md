# Notices and third-party data

Debloater is licensed under the **GNU General Public License v3.0** (see `LICENSE`), because it bundles
GPL-3.0 data from the projects below.

| File | Source | License |
| --- | --- | --- |
| `app/src/main/assets/uad_lists.json` | [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), `resources/assets/uad_lists.json` at commit `69ca5269151ab0788eae7af851090702974a9153`, unmodified | GPL-3.0 |
| `app/src/main/assets/uad_overrides.json` | [System App Remover / Debloater](https://github.com/santhosh-D-subramani/Debloater) by santhosh-D-subramani, `uad_overrides.json` at commit `e6e97dfd4bf8c2b5e7bc35dc82a082314745ba3b`, duplicate keys removed | GPL-3.0 |
| `app/src/main/assets/debloat_list.json` | Written for this project | GPL-3.0 |

Ideas taken from System App Remover (no code copied): marking packages that usually cannot be uninstalled
on a given ROM, and retrying failed removals with another command (here: Disable).

To update the UAD-NG list run `tools/update_uad_list.sh`.
