#!/bin/bash
# v5.0: app flavor'ga qarab mavzu beradi (src/agent/res, src/admin/res) — tekshirishda main + flavor
# birlashtiriladi. Mijoz alohida.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHK="$ROOT/tools/res_check.py"
TMP="$(mktemp -d)"
rc=0
for f in agent admin; do
  rm -rf "$TMP/res"; mkdir -p "$TMP/res"
  cp -r "$ROOT/app/src/main/res/." "$TMP/res/"
  if [ -d "$ROOT/app/src/$f/res" ]; then cp -r "$ROOT/app/src/$f/res/." "$TMP/res/"; fi
  echo "--- app/$f"
  python3 "$CHK" "$TMP/res" || rc=1
done
rm -rf "$TMP"
echo "--- mijoz"
python3 "$CHK" "$ROOT/mijoz/src/main/res" || rc=1
exit $rc
