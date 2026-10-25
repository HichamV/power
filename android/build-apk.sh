#!/bin/bash
# Bouwt Power.apk uit ../krachtlog/krachtlog.html (dezelfde bron als de web-versie).
set -e
A="$(cd "$(dirname "$0")" && pwd)"; SRC="$A/../krachtlog/krachtlog.html"
mkdir -p "$A/app/src/main/assets/www"
{ echo '<!doctype html><html lang="nl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>html{padding:0}body{margin:0}img{max-width:100%}[hidden]{display:none!important}</style></head><body>'; cat "$SRC"; echo '</body></html>'; } > "$A/app/src/main/assets/www/index.html"
cp "$A/../krachtlog/manifest.json" "$A/../krachtlog/icon-192.png" "$A/../krachtlog/icon-512.png" "$A/app/src/main/assets/www/"
if [ "$1" = "ci" ]; then G="./gradlew"; else G="gradle"; export ANDROID_HOME="${ANDROID_HOME:-/home/user/android-sdk}"; fi
cd "$A" && $G --no-daemon -q assembleRelease && cp app/build/outputs/apk/release/app-release.apk "$A/Power.apk" && ls -l "$A/Power.apk"
