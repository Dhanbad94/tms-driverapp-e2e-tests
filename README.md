# TMS Driver App — Appium E2E Tests (Phase A foundation)

Independent Kotlin / Appium (UiAutomator2) / JUnit 5 / Allure framework for the Track My Shuttle Driver
Android app. It never compiles against the app's source. Planning documents live in the app repo under
`automation-planning/`.

**Status:** Phase A foundation written. **Not yet built or executed** — see "Verification status".

## Layout

```
config/environments/*.properties   non-secret settings per flavor (staging, development)
scripts/preflight.sh               reports which prerequisites are present/missing (changes nothing)
src/main/kotlin/tms/driver/
  core/config/EnvConfig.kt         env resolution; rejects the production flavor
  core/driver/DriverFactory.kt     UiAutomator2 capabilities -> AndroidDriver
  core/driver/DriverSession.kt     thread-local session holder
  core/wait/Waits.kt               condition-based waits (no sleeps)
  core/device/Adb.kt               adb helpers (permissions, network, app data) for later phases
  core/report/Artifacts.kt         screenshot / page source / logcat -> disk + Allure
  screens/Ids.kt                   resource-id helper (builds `<appPackage>:id/<name>` from the session)
  screens/BaseScreen.kt, auth/LoginScreen.kt, SystemDialogs.kt
  flows/LaunchFlow.kt
src/test/kotlin/tms/driver/
  base/DriverExtension.kt          session lifecycle + failure diagnostics
  smoke/LaunchSmokeTest.kt         AUTH-001 launch -> Login (needs device)
  config/ConfigSanityTest.kt       config checks (no device needed)
```

## Prerequisites

JDK 17+, Gradle 8.x, Node.js, Appium 2.x with the `uiautomator2` driver, Android platform-tools (`adb`),
an emulator or device with USB debugging, and the **staging-flavor APK** (or the app pre-installed).

```bash
bash scripts/preflight.sh          # lists what is missing
gradle wrapper                     # once, to generate ./gradlew
```

## Configuration (nothing secret is stored in the repo)

Resolution order: environment variable > `-Dtms.<key>` > `config/environments/<env>.properties`.

| Variable | Purpose | Default |
|---|---|---|
| `TMS_ENV` / `-Penv=` | environment file to load | `staging` |
| `TMS_APK_PATH` | APK to install (omit if pre-installed) | none |
| `TMS_APPIUM_URL` | Appium server | `http://127.0.0.1:4723` |
| `TMS_UDID`, `TMS_DEVICE_NAME`, `TMS_PLATFORM_VERSION` | device selection | auto |
| `TMS_RESET_APP` | clear app data on start (`true` = clean state) | `true` |
| `TMS_AUTO_GRANT` | auto-grant runtime permissions | `true` |

Driver credentials (`TMS_DRIVER_PHONE`, `TMS_DRIVER_PASSCODE`) are **not used in Phase A**; when needed
they must come from the environment or a CI secret store. See `.env.example`.

The `production` flavor is refused by `EnvConfig`.

## Commands

```bash
# 1) Config sanity checks - no device, no Appium server
./gradlew test

# 2) Start Appium in another terminal
appium

# 3) Launch smoke test (device + Appium + APK required)
#    Auth slice: ./gradlew authTest -Penv=staging   (needs TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE)
#    Resend OTP (sends real SMS): ./gradlew smsTest -Penv=staging
export TMS_APK_PATH=/path/to/driver-staging.apk
./gradlew smokeTest -Penv=staging

# Allure results are written to build/allure-results ; failure artifacts to build/artifacts/<Class.method>/
allure serve build/allure-results      # if the Allure CLI is installed
```

## Trip setup through the Rider and Driver APIs

A test ride is created through the **Rider API**, found in the driver queue and assigned to the QA driver through the
**Driver API** (contract from the Playwright rider-app suite and the product owner), then checked in the app.
Clients: `src/main/kotlin/tms/driver/backend/RiderApi.kt` and `DriverApi.kt`; local stubs under `src/test/.../backend/`.

| Step | Call | Confirmed on staging |
|---|---|---|
| Create the ride | `POST {rider}/request` with `Authorization: Bearer`, header `token-id: ODASAP`, body from `testdata/ride-request.json` | Yes: `{"code":200,"message":"Request has been added","response":{"ride_code",...,"ride_status":1}}` |
| (Token) | `POST {rider}/guest-user` with the rider gateway `Basic` header, or a ready Bearer | Bearer path verified; guest-user not exercised |
| Driver login | `POST {driver}/login` (Basic gateway + phone, isd, passcode, device fields) | Yes: token whose payload has `result.driver_id` |
| Find the ride | `GET {driver}/requests` | Yes (the staging queue is shared and holds many rides; the unique rider name is the key) |
| Assign | `POST {driver}/assign-request` | Yes: `successCount 1, failedCount 0` |
| Dashboard | `GET {driver}/dashboard` -> `dispatches.count`, `.trip_started` | Yes |
| Cancel | `POST {driver}/dispatch-action` (`action` 2) | Yes: "Dispatch canceled successfully" |
| Start the trip | `POST {driver}/proceed` by the assigned driver (no on-demand start endpoint) | Not exercised yet |

`{rider}` = `https://riderapi-staging.trackmyshuttle.com/rider/api/v1`; `{driver}` = the staging `.../driver/api/v3`.
Both APIs answer HTTP 200 even for failures, so results are judged on the body `code`.

**Order matters.** Confirmed by a live run: logging the QA driver in through the **Driver API signs the app out**. So
`TripLifecycleTest` makes every API call first, logs in to the app afterwards, and does the cleanup (which logs in
again) last. A separate dispatcher account (`TMS_DISPATCHER_PHONE` / `_PASSCODE`) avoids this; with
`TMS_DISPATCHER_USE_QA_DRIVER=true` the QA driver's own credentials are used instead.

Each ride gets a **unique rider name** and a **random, different pickup and drop-off** from `testdata/stop-list.json`
(10 stops); the other rider details (5 guests, ADA, note, phone) come from `testdata/ride-request.json`
(the template holds a placeholder phone: set `TMS_RIDER_PHONE` to the number that should receive the SMS). **Creating a ride sends SMS messages to the rider phone.** Credentials are
read from environment variables only (see `.env.example`); only staging / development / local hosts are accepted
(preproduction shares the production database and is refused, with no override).

- `./gradlew test` runs both clients and the ride fixture against local stubs (43 tests, no device). Findings built in: the Rider API rejects `"note": null` (the key is left out), an unassigned ride is cancelled with `request-action` (assigned ones with `dispatch-action`), and staging refuses a second ride for a driver who already has one waiting to start.
- `./gradlew tripTest` runs the trip tests on the emulator (TRIP-001, Phase T1 `HomeWithDispatchTest` and Phase T2 `StartTripTest`, Phase T3 `TripViewTest`, Phase T4 `ReachDestinationTest`, Phase T5 `RequestsTabsTest` and `PassengerActionsTest`, Phase T6 `RequestsIntakeTest`, Phase T7 `OperatorEventsTest`, `TwoRidesTest`, `StateLifecycleTest` and `MultiRiderStopTest`; see `docs/trip-lifecycle-test-plan.md`). TRIP-001: **passed against real staging** (2026-10-27). It needs the Rider
  API, Driver API and QA driver variables, and is skipped otherwise.
- `./gradlew exploreTest` is a developer tool that saves UI dumps and shows what an API login does to the app session.
- Home-screen ids confirmed live: `atv_label` ("You have 1 dispatch"), `tv_guest` ("5 Guests"), `tv_ada`, `tv_stops`
  ("2 Stops"), `btn_go` ("START TRIP", upper case), `tv_org`, `tv_shuttle`.
- CI: `_e2e.yml` does not yet pass the new variables (`TMS_RIDER_API_URL`, `TMS_RIDER_BEARER`, `TMS_DISPATCHER_USE_QA_DRIVER`
  or `TMS_DISPATCHER_*`); until it does, the trip suite is skipped there.

Test plan for the full trip lifecycle (117 scenarios, phased): [`docs/trip-lifecycle-test-plan.md`](docs/trip-lifecycle-test-plan.md).

## Real devices and release builds

Not yet verified: everything above ran on an emulator against a **debug** build (`debuggable=true`, `v2.0.5`).
To run on a physical device with a release-style build:

1. Enable Developer options and USB debugging, connect the device, accept the RSA prompt.
2. `bash scripts/preflight.sh` - lists each device as `physical` or `emulator` and states whether the APK is debug or release.
3. Select the device and build, then run the same tasks:
   ```bash
   export TMS_UDID=<serial from preflight>
   export TMS_APK_PATH=/path/to/staging-release.apk     # a non-debuggable build
   export TMS_ENV=staging
   ./gradlew authTest -Penv=staging
   ```
4. Things that can differ and must be checked on the first run (not assumed):
   - **resource-ids in a release build** (minified/shrunk): compare a UI dump (`failure-pagesource.xml`) with the debug one;
   - **offline tests** use `adb shell cmd connectivity airplane-mode`, falling back to `svc wifi`/`svc data`; real devices may
     need the fallback or a device that allows it;
   - **OEM permission or battery dialogs**, keyboard layout (the tests hide the keyboard and scroll to buttons),
     and Toast capture via Appium's logcat output;
   - `autoGrantPermissions=true` is used; run once with `TMS_AUTO_GRANT=false` to check the permission prompts.
5. A release build removes Chucker/LeakCanary (debug only), so there are no extra notifications from them.

## CI/CD (GitHub Actions)

| Workflow | When | What |
|---|---|---|
| `.github/workflows/ci.yml` | pull request / push to `main` | `./gradlew test` (no device), then `smokeTest` on an emulator |
| `.github/workflows/driver-staging.yml` | Mon-Fri 06:00 UTC smoke; Tue/Thu 08:00 UTC regression; manual | `smoke`, `auth`, `trip`, `regression` (= smoke + auth + trip, no SMS) or `sms` (needs `confirm_sms`) |
| `.github/workflows/_e2e.yml` | called by the two above | emulator + Appium + Gradle + dashboard + optional Pages and Slack |

Runs share one staging backend and one QA driver account, so they are serialized (`concurrency: driver-e2e-staging`).

Repository **secrets**: `TMS_APK_URL` (staging APK download URL; or pass `apk_url` when dispatching),
`TMS_APK_AUTH_HEADER` (optional, e.g. `Authorization: Bearer ...`), `TMS_DRIVER_PHONE`, `TMS_DRIVER_PASSCODE`,
`TMS_TRIP_API_URL`, `TMS_TRIP_AUTH_VALUE`, `TMS_TRIP_DRIVER_ID` (trip tests), `SLACK_WEBHOOK_URL` (optional).
Repository **variables**: `TMS_DRIVER_ISD`, `TMS_DRIVER_COUNTRY` (optional), `PUBLISH_REPORT=true` (publish the redacted
dashboard to GitHub Pages / `gh-pages`).
`scripts/ci/fetch-apk.sh` refuses any APK whose package is not the staging or development flavor.

## Custom report

`python3 scripts/generate_report.py` (standard library only) turns `build/test-results` (JUnit XML) plus
`build/allure-results` into one self-contained `build/reports/dashboard/index.html` and `summary.json`: pass/fail/skip
cards, failures at the top, breakdown by suite and by area, slowest tests, search and status filters, and per-test
Given/When/Then steps, error text and failure screenshots. `--redact` writes the publishable variant (no
screenshots, one-line errors, QA phone masked). Every CI run adds the summary to the job page, uploads the private
dashboard and evidence as an artifact, and (when enabled) publishes only the redacted one. Artifacts are visible to
anyone who can see the repository, so in a **public** repo only the redacted dashboard and plain test reports are
uploaded (no screenshots, page source or logcat).

Not yet exercised on GitHub: the workflows were validated locally for YAML syntax only, and the CI emulator is set to
320x640 @160dpi to match the local AVD through `pre-emulator-launch-script`. Confirm the first run's log shows
`Display: Physical size: 320x640`.

## Verification status

Executed on an Android 14 emulator (`tms_api34`, started with `-memory 2048 -gpu guest`) against
`com.trackmyshuttle.driver.staging` (`apps/driver-staging.apk`), Appium 2.19.0, 2026-10-19:

| Task | Tests | Result |
|---|---|---|
| `test` | 5 config checks | 5/5 passed |
| `authTest` | 18 tests in `od_asap_driver_app.kt` (class `OdAsapDriverApp`), plain-English titles | 18/18 passed in one full run (2026-10-19) |
| `smsTest` | OTP-001/002/004/005 combined flow (sends real SMS) | passed on an earlier run; the plain-English rewrite of this test has been compiled but **not re-run** (to avoid more SMS) |
| `smokeTest` | AUTH-001 launch -> Login | passed (re-run after the rewrite) |

Covered: AUTH-003/004/005/006/010/012, PASS-001/002/003/006, SES-001/002/003/004, SH-001/002/003/004/009,
PR-001/002, CP-001/002, OTP-001/002/004/005 (see `automation-planning/05-test-scenarios.md`).
Asserted messages: "Invalid Passcode. Please try again.", "Phone not registered. Please contact Manager to get your
number registered.", "Connectivity Issue...", "Please enter your first name", "Please enter your last name",
"Please enter valid email", "Invalid passcode. Please retry.", "Passcodes do not match. Please retry.",
"Sign Out?" dialog text, OTP screen title/subtitle.

Findings from live runs (differences from the Phase 1 code reading):
- resource-ids are `<applicationId>:id/<name>`; the QA driver needs **+91 / India** selected; after the picker closes the
  phone field is disabled and the `v_number_click` overlay must be tapped again;
- the on-screen keyboard hides the terms text and the Edit Profile Save/Cancel buttons (hide keyboard / scroll first);
- this staging build shows only **Home, Profile, Help** tabs (no Notifications tab);
- no connectivity banner on the Login screen (toast only); on Home the offline banner appears;
- the transient "Back online" banner was **not observed** in the run (recovery is asserted via the banner disappearing);
- toasts are exposed for ~0.4 s and appear ~3 s after the tap, so they are read from Appium's logcat output
  ("Adding toast message to root: ...") rather than the hierarchy; call `Toasts.mark()` before the triggering action;
- a wrong 6-digit OTP shows "Invalid code. Please try again." and clears the field;
- `sendKeys` replaces (does not append to) the text of a field;
- the Sign Out dialog confirm button is `btn_positive_vert`;
- one run showed an Android "isn't responding" (ANR) dialog while switching tabs on the software-rendered
  emulator (not reproduced in the final run; `anr-dialogs-observed=0`).

Test cases in plain English: see `TEST_CASES.md`; the report shows the same Given/When/Then steps.

Environment notes: the emulator crashed twice with `-gpu swiftshader_indirect` ("Failed to bind to texture: invalid color buffer"); `-gpu guest` was stable for a full 11-minute run. The AVD in `~/.android/avd/tms_api34.avd/config.ini` has `hw.ramSize = 96M`; with that setting the
emulator crashed mid-run, so it is started with `-memory 2048`. Start emulator and Appium detached (`setsid nohup`) so a
dropped terminal does not kill a run.

Still unverified: real devices, other Android versions, release builds, `autoGrantPermissions=false`.
The staging APK reports version `v2.0.5` on the Profile screen (source tree is `v2.0.6`).

Credentials come from environment variables (`TMS_DRIVER_PHONE`, `TMS_DRIVER_PASSCODE`, optional
`TMS_DRIVER_ISD`, `TMS_DRIVER_COUNTRY`); failure screenshots can show the typed phone number, so keep
`build/artifacts` out of shared reports. The duty-toggle test changes and restores the driver's duty state; the
`sms` test sends real SMS messages (Forgot passcode and Resend) to the QA phone.
