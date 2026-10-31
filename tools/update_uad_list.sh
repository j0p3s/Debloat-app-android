#!/usr/bin/env bash
# Downloads the latest Universal Android Debloater Next Generation list (GPL-3.0) into the app assets.
# Run from the repository root, then run the unit tests and update the commit in NOTICE.md.
set -euo pipefail
url="https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
curl -fsSL "$url" -o app/src/main/assets/uad_lists.json.tmp
python3 -c "import json,sys; json.load(open(sys.argv[1]))" app/src/main/assets/uad_lists.json.tmp
mv app/src/main/assets/uad_lists.json.tmp app/src/main/assets/uad_lists.json
echo "Updated uad_lists.json. Now run ./gradlew testDebugUnitTest"
