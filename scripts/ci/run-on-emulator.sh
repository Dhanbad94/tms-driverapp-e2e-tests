#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (an emulator is already booted).
# Starts Appium, runs the given Gradle test task(s), and records the Gradle exit code in
# build/ci-exit-code so the workflow can still build the report before it fails the job.
#
#   usage: scripts/ci/run-on-emulator.sh "<gradle tasks>"
set -uo pipefail

tasks="${1:?usage: run-on-emulator.sh \"<gradle tasks>\"}"
mkdir -p build

echo "Device: $(adb shell getprop ro.product.model | tr -d '\r') / Android $(adb shell getprop ro.build.version.release | tr -d '\r')"
echo "Display: $(adb shell wm size | tr -d '\r') $(adb shell wm density | tr -d '\r')"

appium --log build/appium.log --log-level info >/dev/null 2>&1 &
appium_pid=$!
trap 'kill "$appium_pid" 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
  curl -fs http://127.0.0.1:4723/status >/dev/null && break
  sleep 2
done
curl -fs http://127.0.0.1:4723/status >/dev/null || { echo "Appium did not become ready"; echo 3 > build/ci-exit-code; exit 0; }

# shellcheck disable=SC2086  # $tasks is intentionally split into several Gradle tasks
./gradlew $tasks --continue --stacktrace -Penv="${TMS_ENV:-staging}"
echo $? > build/ci-exit-code
exit 0
