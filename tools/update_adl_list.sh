#!/usr/bin/env bash
# Downloads the latest Android Debloat List (AGPL-3.0, https://github.com/MuntashirAkon/android-debloat-list)
# into app/src/main/assets/adl. Run from the repository root, then run the unit tests and update NOTICE.md.
set -euo pipefail
tmp=$(mktemp -d)
git clone --depth 1 https://github.com/MuntashirAkon/android-debloat-list "$tmp/adl"
for f in aosp carrier google misc oem; do
  python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$tmp/adl/$f.json"
done
rm -rf app/src/main/assets/adl
mkdir -p app/src/main/assets/adl/suggestions
cp "$tmp"/adl/{aosp,carrier,google,misc,oem}.json "$tmp/adl/COPYING" app/src/main/assets/adl/
cp "$tmp"/adl/suggestions/*.json app/src/main/assets/adl/suggestions/
echo "ADL commit: $(git -C "$tmp/adl" rev-parse HEAD)"
rm -rf "$tmp"
echo "Updated. Now run ./gradlew testDebugUnitTest"
