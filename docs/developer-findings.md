# Findings for the developers (staging, Driver app, ASAP mode)

Collected by the automation suite. Each item has the evidence and a suggested fix. Nothing here is a test failure of the suite itself.

## 1. Assigning a ride to a driver outside the organisation's region answers a bare 500 (found 2026-11-23)

**What happens**
- `POST /driver/api/v3/assign-request` (and the app's ASSIGN TO SELF, which calls the same API) answers `{"code":500,"message":"Something went wrong","response":{}}` when the driver's last known position is **outside the organisation's region** (Chicago). The Assign Driver screen (`drivers-for-dispatch`) also fails and the app silently returns to the previous screen.
- The same assignment answers `{"code":200,"message":"Data updated"}` as soon as the driver's position is inside the region.
- It worked with the same out-of-region position (Mountain View, 37.4220, -122.0841) on 2026-10-29, so the check was added or tightened after that.

**Evidence (2026-11-23)**
- Same QA driver, same shuttle, same ride request, only the driver's position changed: Mountain View (37.4220, -122.0841) gives 500 on every attempt (8 attempts, different stops and party sizes, API and app); downtown Chicago (41.8781, -87.6298) gives 200.
- Ride creation (`/rider/api/v1/request`) and every read call work in both cases.
- Ruled out: shuttle state (both shuttles unassigned, then selected again), stop data, whether the app is logged in, the driver's duty state.

**Why it matters**
- A driver, dispatcher or test cannot tell what is wrong: "Something went wrong" says nothing, the app shows no message at all on the failed screens, and the server log is the only place to learn the cause.
- It looks like an unhandled exception in the region check, not a deliberate refusal.

**Suggested behaviour**
1. Answer a deliberate, specific refusal instead of a 500. Suggested body (the API reports results in the body `code`; 422 already means "invalid parameter" there, so a dedicated code is also fine):
   ```json
   {"code": 422,
    "message": "This ride cannot be assigned because the driver's current location is outside the service area of <organisation name>. Move into the service area, or ask your manager to review the area.",
    "response": {"reason": "DRIVER_OUTSIDE_SERVICE_AREA"}}
   ```
2. Show that message in the Driver app (toast or dialog) for ASSIGN TO SELF, the Assign Driver confirmation and the Requests tab, and on the Assign Driver screen either list drivers outside the area as disabled with the reason, or say so, instead of closing the screen without a word.
3. When the driver has no position at all (no recent location), answer a different clear message: "The driver's location is not available. Open the app, allow location access and try again." (`DRIVER_LOCATION_UNAVAILABLE`).
4. Log the exception with a request id on the server and return that id in `response` so a support person can find it.
5. Keep the 500 for real failures only.

**How the suite copes:** every test now puts the emulator in downtown Chicago (41.8781, -87.6298) before it starts, which is inside the region and about 25 km from the O'Hare test stops. Override with `TMS_DEVICE_LATITUDE` / `TMS_DEVICE_LONGITUDE`.

**Question for the product owner:** is the service-area check on assignment intended? If yes, should an operator be able to assign to a driver outside the area?

## 2. Other candidates already recorded

- Change Drop-off fails silently: if the stop list cannot load the app returns to the trip view (or Home when offline) with no message, and a failed UPDATE shows nothing.
- A second ride can only be handed over to a running trip with the same pickup as the first ride; a different pickup answers 500 "Something went wrong".
- The rider sheet hides room and flight on the trip view AND on the Arrived screen, although the Requests card shows them (PAX-001, PSNG-001).
- On the Arrived screen at the drop-off stop the seat counter reads 0 while the picked-up rider is still on board (seen 2026-11-26; meant that way?).
- Driver-API actions on a not-started ride (cancel, complete, change driver) do not produce the "Dispatch canceled / completed / has changed" sheets the app has code for.
- Smaller items: "Please enter valid name " has a trailing space; the photo size check only triggers at 2 MB or more; a denied camera permission shows no message; the Dispatch Log status bar has no text.
- Dead code to clean up: Mark Arrived dialog, the map's "shuttle location" banner, `GuestInfoAdapter`, `FragmentSearchRides`, `ChangeStopFragment`, `DispatchLogFragment`, the hidden side menu, `ChangeShuttleFragment`, about 20 unused strings.
