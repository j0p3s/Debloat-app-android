# Debloater 1.10.2

## Cache application-type selector
- The cache scanner now has an Application type button with User apps, System apps and Both.
- Cache totals, unavailable-size counts, application lists and clearing targets follow the chosen type.
- Switching types reuses the current scan. Choosing Both shows user and system caches in one list with their separate totals.
- Select shown and Deselect apply to the current type. Selections in other types are retained but are excluded from the clear confirmation until their type (or Both) is chosen.
- The choice survives screen rotation. Existing search, size filters and sorting continue to apply.
- New labels are translated into all 11 supported languages.

## Compatibility
Version 1.10.2, version code 14. Same application ID and permanent signing identity as 1.10.1. Android 11 or later. Package operations require Shizuku; measurements require Usage access.

## Verification
Translation coverage, unit tests (including category totals and clear-target isolation), debug build, Android lint, release signing guard, signed release build and certificate verification run in CI. On-device visual checks remain pending.
