#!/usr/bin/env bash
# Adds Material Symbols (Rounded, weight 300, Apache 2.0) as vector drawables named ic_<name>.xml.
# Usage: scripts/add-icons.sh close undo redo   (names from fonts.google.com/icons)
set -euo pipefail
out="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/drawable"
for name in "$@"; do
  svg=$(curl -sSfL "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/$name/materialsymbolsrounded/${name}_wght300_24px.svg")
  d=$(grep -oP '<path d="\K[^"]+' <<<"$svg" | head -1)
  # Downloaded data goes into XML: accept path commands and numbers only.
  [[ "$d" =~ ^[MmLlHhVvCcSsQqTtAaZz0-9.,\ -]+$ ]] || { echo "unexpected path data for $name" >&2; exit 1; }
  cat > "$out/ic_$name.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "$name", weight 300 (Apache 2.0). Added by scripts/add-icons.sh. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path android:fillColor="#FFFFFFFF" android:pathData="$d" />
    </group>
</vector>
EOF
  echo "ic_$name"
done
