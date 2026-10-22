#!/usr/bin/env bash
# Downloads the staging APK, refuses anything that is not the staging/development flavor, and writes
# build/ci-meta.json for the dashboard header. Exports TMS_APK_PATH to later steps via GITHUB_ENV.
#
#   TMS_APK_URL          required: where to download the APK (workflow input or secret)
#   TMS_APK_AUTH_HEADER  optional: full header line for the download, e.g. "Authorization: Bearer <token>"
set -euo pipefail

url="${TMS_APK_URL:-}"
if [ -z "$url" ]; then
  echo "::error::No APK configured. Set the TMS_APK_URL repository secret or pass the apk_url input."
  exit 2
fi

mkdir -p apps build
out="apps/driver-staging.apk"
args=(--fail --location --silent --show-error --retry 3 --output "$out")
[ -n "${TMS_APK_AUTH_HEADER:-}" ] && args+=(--header "$TMS_APK_AUTH_HEADER")
curl "${args[@]}" "$url"

# An APK is a zip ("PK"). Hosting pages (Google Drive quota/sign-in/virus-scan pages) answer 200 with HTML,
# so say what actually came back instead of failing later with an opaque aapt2 error.
if [ "$(head -c 2 "$out")" != "PK" ]; then
  echo "::error::The download is not an APK ($(stat -c %s "$out") bytes, $(file -b "$out" | cut -c1-60))."
  title="$(grep -o -i '<title>[^<]*' "$out" | head -1 | sed 's/<title>//I')"
  [ -n "$title" ] && echo "::error::The server returned a web page titled: $title"
  rm -f "$out"
  exit 2
fi

aapt2="$(ls "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$aapt2" ]; then
  echo "::error::aapt2 not found under the Android SDK build-tools"
  exit 2
fi
badging="$("$aapt2" dump badging "$out")"
pkg="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")"
vname="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
vcode="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"

case "$pkg" in
  com.trackmyshuttle.driver.staging | com.trackmyshuttle.driver.development) ;;
  *)
    echo "::error::Refusing APK with package '$pkg'. Expected the staging or development flavor (never production)."
    rm -f "$out"
    exit 2
    ;;
esac

echo "APK package=$pkg version=$vname ($vcode) size=$(du -h "$out" | cut -f1)"
echo "TMS_APK_PATH=$PWD/$out" >> "$GITHUB_ENV"
python3 - "$pkg" "$vname" "$vcode" <<'PY' > build/ci-meta.json
import json, os, sys
pkg, vname, vcode = sys.argv[1:4]
run = f"{os.environ.get('GITHUB_SERVER_URL','')}/{os.environ.get('GITHUB_REPOSITORY','')}/actions/runs/{os.environ.get('GITHUB_RUN_ID','')}"
print(json.dumps({
    "App": f"{pkg} {vname} ({vcode})",
    "Environment": os.environ.get("TMS_ENV", "staging"),
    "Commit": os.environ.get("GITHUB_SHA", "")[:8],
    "Branch": os.environ.get("GITHUB_REF_NAME", ""),
    "Trigger": os.environ.get("GITHUB_EVENT_NAME", ""),
    "Run": run if os.environ.get("GITHUB_RUN_ID") else "",
}))
PY
