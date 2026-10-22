#!/usr/bin/env bash
# Reports which prerequisites for the device-backed smoke test are present. Makes no changes.
set -u
ok=0; miss=0
check() { # name, command-to-test, hint
  if eval "$2" >/dev/null 2>&1; then echo "  [ok]      $1"; ok=$((ok+1)); else echo "  [MISSING] $1  -> $3"; miss=$((miss+1)); fi
}
echo "Toolchain"
check "JDK 17+ (javac)"        'javac -version 2>&1 | grep -Eq "(1[7-9]|[2-9][0-9])\."' "install a JDK 17+ (a JRE is not enough)"
check "Gradle (or ./gradlew)"  'command -v gradle || test -x ./gradlew' "install Gradle 8.x, then run: gradle wrapper"
check "Node.js"                'command -v node' "install Node.js LTS"
check "Appium 2.x"             'command -v appium' "npm i -g appium && appium driver install uiautomator2"
check "UiAutomator2 driver"    'appium driver list --installed 2>&1 | grep -qi uiautomator2' "appium driver install uiautomator2"
check "adb"                    'command -v adb' "install Android platform-tools; set ANDROID_HOME"
echo "Device"
check "device/emulator attached" 'adb devices 2>/dev/null | awk "NR>1 && \$2==\"device\"" | grep -q .' "start an emulator or connect a device with USB debugging"
echo "Application"
APK="${TMS_APK_PATH:-}"
check "APK (TMS_APK_PATH)"     '[ -n "$APK" ] && [ -f "$APK" ]' "export TMS_APK_PATH=/path/to/staging.apk (or pre-install the app)"
echo "Appium server"
URL="${TMS_APPIUM_URL:-http://127.0.0.1:4723}"
check "server reachable at $URL" 'curl -fsS "$URL/status"' "start it with: appium"
echo "Target details (informational)"
export PATH="$PATH:${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools"
if command -v adb >/dev/null 2>&1; then
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}' | while read -r serial; do
    model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    rel=$(adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
    kind=$([ "$(adb -s "$serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" = "1" ] && echo emulator || echo physical)
    echo "  device $serial: $kind, $model, Android $rel   (select with TMS_UDID=$serial)"
  done
fi
AAPT=$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/aapt2 2>/dev/null | tail -1)
if [ -n "${APK:-}" ] && [ -f "$APK" ] && [ -n "$AAPT" ]; then
  pkg=$("$AAPT" dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*versionName='\([^']*\)'.*/\1 \2/p")
  dbg=$("$AAPT" dump badging "$APK" 2>/dev/null | grep -q application-debuggable && echo "DEBUG build (debuggable)" || echo "release-style build (not debuggable)")
  echo "  apk: $pkg - $dbg"
fi
echo; echo "present=$ok missing=$miss"
exit $([ $miss -eq 0 ] && echo 0 || echo 1)
