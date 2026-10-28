# Test cases in plain English

Each case below is automated by a test with the same title (see `src/test/kotlin/tms/driver/auth/od_asap_driver_app.kt`
and `smoke/LaunchSmokeTest.kt`). The test report shows the same Given / When / Then steps. The ID in brackets links to
`automation-planning/05-test-scenarios.md`.

Unless stated otherwise each case starts with the app freshly installed (no saved data), so it begins on the Login screen,
and uses the QA driver's phone number and passcode supplied through environment variables.

## Opening the app

**[AUTH-001] A first launch with no saved session opens the Login screen**
- *Given* the app is installed with no saved data
- *When* the app is opened
- *Then* the Driver app is in the foreground and the Login screen is showing.

## Login screen

**[AUTH-003] Tapping the phone number area opens the login form**
- *Given* the Login screen is showing, the phone field is disabled and the terms text is hidden
- *When* the driver taps the phone number area
- *Then* the phone field becomes usable, the terms text appears and Submit stays disabled.

**[AUTH-004] Submit is enabled only when a full phone number has been typed**
- *Given* the login form is open and the phone number is empty, Submit is disabled
- *When* the driver types 1 digit, then 2 digits, Submit stays disabled
- *And when* the driver types a 10-digit number, Submit becomes enabled
- *Then* clearing the field disables Submit again.

**[AUTH-005] The phone number field stops accepting digits after 15**
- *When* the driver types 20 digits, *then* only the first 15 are kept.

**[AUTH-006] An unregistered phone number is rejected with a clear message**
- *Given* the login form is open with the driver's country selected
- *When* the driver enters a random 10-digit number that is not registered and taps Submit
- *Then* the message "Phone not registered. Please contact Manager to get your number registered." is shown and Submit is hidden
- *And* editing the number clears the message.

**[AUTH-010] Choosing a country in the country picker updates the dial code**
- *When* the driver opens the country picker and chooses the driver's country (India)
- *Then* the dial code next to the phone number shows +91.

**[AUTH-012] Submitting the phone number while offline shows a connectivity message**
- *Given* the form is filled in with the driver's phone number
- *When* the device goes offline and the driver taps Submit
- *Then* a message starting "Connectivity Issue" is shown. (The Login screen has no offline banner.)

## Passcode screen

**[PASS-001] A driver with the correct passcode reaches the home screen**
- *When* the driver enters their phone number and the correct 4-digit passcode
- *Then* the home screen is shown with its toolbar and bottom navigation, and no organisation picker.

**[PASS-002] A wrong passcode is rejected with a clear message**
- *When* the driver enters a wrong 4-digit passcode and taps Submit
- *Then* "Invalid Passcode. Please try again." is shown and the driver stays on the passcode screen.

**[PASS-003] Submit is available only once exactly four passcode digits are entered**
- *Given* the passcode screen titled "Enter passcode to login" with a "Forgot passcode" link
- *When* 0, 1 and 3 digits are entered, Submit is not enabled
- *And when* the fourth digit is entered, Submit is enabled
- *Then* a fifth digit is ignored and Submit stays enabled.

**[PASS-006] The passcode is hidden while it is being typed**
- *When* the driver types the passcode, *then* the digits are not readable as plain text on screen.

## Staying logged in and signing out

**[SES-001] A logged-in driver stays logged in after the app is closed and reopened**
- *When* the app is closed completely and opened again, *then* the home screen opens without a new login.

**[SES-002 / SES-004] Signing out from Profile asks for confirmation and fully ends the session**
- *When* the driver opens Profile and taps Sign Out, *then* "Sign Out?" asks "Are you sure you want to sign out?"
- *And* choosing Cancel keeps the driver logged in
- *When* the driver signs out and confirms, *then* the Login screen is shown, the country is remembered and the phone number is empty
- *And* reopening the app does not bring the session back.

**[SES-003 / SH-003 / SH-004] Switching duty on and off shows or hides the toolbar Sign Out button**
- *Given* the home screen shows the driver's current duty state
- *When* the driver switches duty, *then* the duty state changes and the toolbar Sign Out button is visible only while off duty
- *And* the original duty state is restored afterwards.

**[SES-003b] The toolbar Sign Out button logs the driver out immediately, with no confirmation**
- *Given* the driver is off duty
- *When* the driver taps the toolbar Sign Out button
- *Then* no confirmation appears and the Login screen is shown. (Duty is restored to ON afterwards if it was ON.)

## Home screen

**[SH-001 / SH-002] The home screen greets the driver and the bottom tabs open their screens**
- *Then* the toolbar shows the driver's name and the Home, Profile and Help tabs are available (this staging build has no Notifications tab)
- *When* the driver taps Profile, then Help, then Home, *then* each tab opens and no "isn't responding" dialog appears.

**[SH-009] Losing the network shows an offline banner and the app recovers when it returns**
- *When* the device loses its network, *then* an offline banner is shown
- *When* the network comes back, *then* the offline banner disappears. (The short green "Back online" banner was not observed.)

## Profile and Change Passcode

**[PR-001 / PR-002] Profile shows the driver's details and Edit Profile rejects missing or invalid fields**
- *Then* the Profile tab shows the driver's name and the app version
- *When* the first name is cleared and Save is tapped, *then* "Please enter your first name" is shown
- *When* the last name is cleared and Save is tapped, *then* "Please enter your last name" is shown
- *When* an invalid email is typed and Save is tapped, *then* "Please enter valid email" is shown
- *And* leaving without saving changes nothing.

**[CP-001 / CP-002] Change Passcode rejects a wrong current passcode and a new passcode that does not match**
- *When* a wrong current passcode is entered, *then* "Invalid passcode. Please retry." is shown
- *When* the correct current passcode is entered, *then* the driver is asked to "Set New Passcode"
- *When* the new passcode and its confirmation differ, *then* "Passcodes do not match. Please retry." is shown. Nothing is changed.

## One-time passcode screen (sends real SMS messages to the QA phone)

**[OTP-001 / OTP-002 / OTP-004 / OTP-005] The OTP screen shows the right titles, needs six digits, rejects a wrong code and can resend**
- *Given* the driver taps "Forgot passcode"
- *Then* the screen is titled "Forgot passcode?" and asks for the 6-digit code sent to the phone
- *When* 5 digits are typed, Next stays disabled; with 6 digits Next is enabled
- *When* a wrong 6-digit code is submitted, *then* an error ("Invalid code. Please try again.") is shown and the field is cleared
- *When* "Resend Code" is tapped, *then* the driver stays on the OTP screen with an empty field
- *Then* going Back leaves the OTP screen.

## Trip setup through the Driver API

**[API-001..016] The Driver API client logs in, finds, assigns, starts and cleans up rides** *(runs against a local stub; no device)*
- A dispatcher with the right gateway credential and passcode can log in.
- A wrong or missing gateway credential is reported as 401 even though the HTTP status is 200.
- A wrong passcode is reported as 400.
- A ride waiting in the queue is found by its rider name, and waiting for one that never appears fails with a clear message.
- Assigning a queued ride succeeds and removes it from the queue; assigning one that does not exist fails with 400.
- An assigned ride can be completed or cancelled with a readable confirmation message; an unknown dispatch is rejected with 400.
- Proceeding after an assignment starts the trip (`trip_started` goes from 0 to 1).
- Cleanup never throws, even when the ride is already gone.
- Production and preproduction hosts are refused; staging is accepted.
- Credentials never appear when the connection settings are logged.

*The contract comes from the Playwright rider-app suite and is not yet verified against the staging API.*

**[RIDER-001..010] The Rider API client and ride test data create rides correctly** *(runs against a local stub; no device)*
- The supplied stop list loads with ten stops, each with an id, name and coordinates.
- A random pickup and drop-off pair always uses two different stops.
- A new ride keeps the template's rider details and gets the unique name that was asked for.
- The request JSON has the riders and both stops, and keeps flight and room as null.
- A guest token is obtained with the gateway credential and used to create the ride; a ready Bearer token is used directly.
- A wrong gateway credential is reported as 401; a wrong organisation token-id and identical pickup/drop-off are rejected with 400.
- A ride created through the Rider API shows up in the Driver API queue and can be assigned.
- A production rider host is refused, and credentials never appear in the settings text.

**[TRIP-001] A ride created through the Rider API and assigned through the Driver API appears on the driver's home screen** *(passed on staging, 2026-10-27)*
- *Given* the Driver API shows the QA driver has no dispatches
- *When* a ride is created through the Rider API with a unique rider name and a random pickup and drop-off, found in the driver queue and assigned to the QA driver, and the QA driver then logs in to the app
- *Then* the home screen shows "You have 1 dispatch", the ride's guest count ("5 Guests"), the ADA flag, "2 Stops" and a Start Trip button
- *And* the ride is cancelled afterwards so nothing stays active.
- Needs the Rider API, Driver API and QA driver settings; creating the ride sends SMS messages to the rider phone.

## Home screen with an assigned ride (Phase T1)

Rides are created through the Rider API and assigned through the Driver API before the app login; every test cleans up and the
fixture fails the test if the QA driver is left with a ride. Each ride sends an SMS to the rider phone.

**[HOME-005] With no ride assigned the Home screen says there are no trips** *(passed on staging, 2026-10-27)*
- *Given* the QA driver has no rides, *when* the driver logs in, *then* Home shows "You have No Trips Assigned" with its picture, no ride card and no START TRIP button.

**[HOME-002 / HOME-003] Home summarises the ride's guest count and ADA flag** *(2 variants, passed on staging)*
- *Given* a ride for a single rider who does not need ADA, *then* Home shows "You have 1 dispatch", "1 Guest" (singular), "2 Stops", START TRIP and no ADA row.
- *Given* a ride for a party of 10 that needs ADA, *then* Home shows "10 Guests" and the ADA row.

**[HOME-001 / 006 / 007 / 008 / 010 / 012] A driver with one ride sees it, can refresh and restart, cannot go off duty, and sees it vanish when cancelled** *(passed on staging)*
- *Given* a ride for 5 guests with ADA, *then* Home shows "You have 1 dispatch", "5 Guests", ADA, "2 Stops", START TRIP, the organisation and the shuttle
- *And* the Requests button shows the number of waiting rides, with "You have new ride requests." when there are some
- *When* the driver pulls down to refresh, *then* the same single ride is still shown
- *When* the driver tries to go off duty, *then* "Action Required: An active dispatch is in progress. Please complete your current trip before going off duty" appears, and OKAY closes it with duty still ON
- *When* the app is closed and reopened, *then* the ride is still there
- *When* the ride is cancelled through the API and the driver logs in again, *then* Home shows no trips.

**[STATE-005] The ride fixture always leaves the QA driver with no rides** *(proved offline with stubs, and live after failed tests)*
- Cleanup cancels every ride the test created (unassigned rides with the queue call, assigned rides with the dispatch call), clears leftovers from earlier runs, and fails loudly if a ride cannot be removed.

## Starting the trip and choosing a shuttle (Phase T2)

Each test assigns one ride (from "Terminal 5E" to "Door 3 - Bus/Shuttle Ctr") before the app login and the fixture cleans up afterwards.

**[START-001 / 002 / 011 / 012 / 015] START TRIP asks to confirm the shuttle once, opens one trip view, and CONTINUE TRIP returns to it without asking again** *(passed on staging, 2026-10-27)*
- *When* the driver taps START TRIP twice quickly, *then* one "Confirm Shuttle" dialog says "You are in <the driver's shuttle>." with CONFIRM and SELECT DIFFERENT SHUTTLE
- *When* the driver confirms, *then* the trip view opens at the pickup with "I've Arrived", LIST VIEW, MAP VIEW and an estimated time
- *When* the driver goes Back once, *then* Home shows "Current Trip" and CONTINUE TRIP
- *And* tapping CONTINUE TRIP opens the trip view again without the dialog.

**[START-004 / 005 / 006 / 007] Choosing a different shuttle lists the ride's needs and warns about an unsuitable shuttle** *(passed)*
- *Given* a ride for 12 guests who need ADA, *when* the driver taps START TRIP and SELECT DIFFERENT SHUTTLE, *then* the picker shows "Select Shuttle", "12 Seats", "ADA", both shuttles, the driver's own selected, and a START TRIP button
- *When* the driver taps Automated Shuttle-2, *then* an "Alerts" sheet lists "Belongs to another driver", "Shuttle too small" and "Requires ADA"
- *And* PICK ANOTHER leaves it unselected, SELECT ANYWAY keeps it selected, and leaving without confirming changes nothing.

**[START-009] With the device's location switched off the trip does not start and the driver is sent to Location settings** *(passed)*
- *When* location is off and the driver taps START TRIP, *then* "Provide GPS Access." says "Location Data is required so that driver can see your location on map."
- *And* OK opens the device's Location settings, and after returning Home still offers START TRIP.

**[START-010] Starting the trip while offline does not open the trip, and starting again once back online does** *(passed)*
- *When* the device is offline and the driver taps START TRIP and confirms, *then* the trip does not open and Home still offers START TRIP; once back online, START TRIP opens the trip.

*Deferred:* START-008 (location permission denied) needs a real-device run; START-013 (shuttle rename) and START-014 (off duty) are optional.

## Trip view and arriving at the pickup (Phase T3)

**[TRIPV-001 / 002 / 003 / 004 / 005 / 011] The trip view lists the pickup and drop-off with their riders, switches between list and map, refreshes and shows rider details** *(passed on staging, 2026-10-27)*
- *Given* a ride from Terminal 5E to Door 3 for 5 guests with ADA and the note "Hello", started by the driver, *then* the screen is titled "Driving to" with LIST VIEW, MAP VIEW and "I've Arrived"
- *And* the list has the pickup card first and the drop-off second, each with its address, and a later-or-equal estimated time for the drop-off
- *And* each card has the rider's chip with the party size of 5
- *When* the driver pulls down to refresh, *then* the same two stops are listed
- *When* the driver opens MAP VIEW, *then* the pickup is the destination with "I've Arrived" and the list cards are gone; LIST VIEW brings them back
- *When* the driver taps the rider, *then* the sheet shows the name as typed, 5, both stops, the note "Hello", CANCEL and PICK UP; CANCEL closes it.

**[TRIPV-006 / 007] I've Arrived opens one 'Arrived At' screen for the pickup, and Back returns to the trip view and then Home** *(passed)*
- *When* the driver taps "I've Arrived" twice quickly, *then* one screen opens for the pickup with the PICKED UP and NO SHOW headers, "Seats Occupied 0 / 15", BACK and GO TO NEXT STOP
- *When* the driver goes Back once, *then* the trip view is shown; going Back again shows Home with "Current Trip", "2 Stops to go" and CONTINUE TRIP.

*Moved to a later phase:* "Go here instead" (TRIPV-010) does not appear with a single ride; automatic arrival (TRIPV-008, 009) needs location simulation.

## Reaching the destination and completing the trip (Phase T4)

**[ARR-001 / 002 / 003 / 004 / 005 / 006 / 008 / 009 / 017] A driver picks the rider up, moves to the drop-off and completes the trip** *(passed on staging, 2026-10-27)*
- *Given* a started trip with one rider, *when* the driver arrives at the pickup, *then* the seats read 0 and GO TO NEXT STOP is disabled until the rider is marked
- *When* the driver marks the rider picked up, *then* the seats show the party size; changing the mark adjusts them; leaving with a mark asks "Go back?" and STAY ON PAGE keeps it
- *When* the driver goes to the next stop, *then* the drop-off is shown, and at the last stop the trip completes with "Trip completed" and Back To Home shows the empty state.

**[ARR-007 / ARR-019] Marking the only rider a no-show completes the trip, and REVIEW opens the Ride Requests screen** *(passed)*

**[ARR-014] GO TO NEXT STOP while offline keeps the screen and the choice, and works once the network is back** *(passed)*

**[ARR-013] At the last stop the trip finishes by itself after about 10 seconds if the driver does nothing** *(passed)*

**[ARR-018] After a completed trip a new ride starts fresh, with no seats left over from the last one** *(passed)*

*Not applicable or moved:* ARR-011 (no seat plus/minus on this build); ARR-012, 015, 016 need two rides on the driver (Phase T7).

## Requests tabs and actions on a ride (Phase T5)

**[REQ-005] The Assigned and In-progress tabs say so when nothing is assigned** *(passed)*
- *Given* no ride, *when* the driver opens Ride Requests > Assigned and In-progress, *then* they read "No Assigned Rides" and "No In-Progress Rides"; the tabs are Requests, Assigned, In-progress and Completed.

**[REQ-001 / REQ-007 / PAX-002] The Assigned tab shows the ride with its details and a menu that opens the dialer** *(passed)*
- *Given* a ride for 5 guests with ADA and the note "Hello", *then* the Assigned badge is 1 and the card shows "Trip not Started by Driver", both stops, the rider, 5, ADA, the note, ASSIGNED TO YOU and ASSIGN TO DRIVER
- *When* the driver opens the three dots, *then* the menu offers Move to Request Queue, Cancel Dispatch, Mark as Completed and the rider's number; tapping the number opens the dialer with that number.

**[REQ-002] The In-progress tab shows a ride once the trip has started** *(passed)* — status "Shuttle on the way to Pick-up"; the ride has left the Assigned tab.

**[PAX-005 / REQ-003 / REQ-007] Mark as Completed finishes the ride at once and lists it under Completed** *(passed)* — "Drop-off Completed (at hh:mm)", "Automation Driver (You)", and Home shows no trip.

**[PAX-006 / REQ-003] Cancel Dispatch asks for a reason and then shows the ride as cancelled by the driver** *(passed)* — four reasons, DONE disabled until one is chosen, backing out keeps the ride, then "Canceled By Driver (at hh:mm)".

**[PAX-004 / REQ-007] Move to Request Queue puts the ride back among the unassigned requests** *(passed)*.

**[PAX-010] The rider menu offers Move to Request Queue, Change Drop-off, Assign to another Driver and the number, and nothing else** *(passed)*.

**[PAX-008] Change Drop-off lists the current stops, can be cancelled, and moves the drop-off to the stop the driver picks** *(passed)* — Terminal 5E to Door 3, changed to Mannheim Rd.

**[PAX-003] PICK UP from the rider sheet needs a second tap to confirm** *(passed)*.

**[PAX-009] ASSIGN TO DRIVER opens the Assign Driver screen with the available drivers, and leaving it keeps the ride with this driver** *(passed)* — the reassignment itself is not automated (the second QA driver is not offered in the list).

**[PAX-001] The Assigned card shows the room number, the flight number and the note** *(passed)*.

**[PAX-007] Cancelling with the reason 'Other' asks for text, and DONE stays off until some is typed** *(passed)*.

**[REQ-006] A finished ride on the Completed tab offers only the rider's number in its menu** *(passed)*; **[PAX-005 extra] a started ride's menu no longer offers Move to Request Queue** *(passed)*.

**Skipped until the developer's fix:** *[PAX-001]* the rider details sheet shows the room and flight number (the sheet hides them today); *[PAX-009]* a ride assigned to Automation Driver 2 leaves this driver and shows up for the other one (the driver is not offered in the list today).

## Taking a ride from the Requests tab (Phase T6)

**[INT-001 / 002 / 003 / 004] A waiting ride is listed with its details and the two assign buttons** — "Requested Just now", both stops, the rider's name in title case, 5 guests, ADA, room 412, flight UA1234, the note, ASSIGN TO DRIVER and ASSIGN TO SELF.

**[INT-018] The sort sheet offers Oldest First, Newest First and Rider Name, and reorders the list** — the new ride is first under Newest, and Rider Name is A to Z.

**[INT-019] Search needs two characters, finds the ride by its name and says 'No Results Found' otherwise.**

**[INT-012 / INT-006] ASSIGN TO SELF stages the ride with a CONFIRM & START TRIP bar, and CANCEL assigns nothing.**

**[INT-005 / INT-006] Confirming ASSIGN TO SELF assigns the ride, starts the trip and opens the trip view** — then the ride is In-progress and Home shows "Current Trip".

**[INT-013] With location services off, assigning a ride asks for GPS access and assigns nothing.**

**[INT-014] A party larger than the shuttle's 15 seats cannot be assigned** — "Shuttle doesn't have enough capacity, please select another."

**[INT-021] A long rider name with accents and punctuation is shown without hiding the assign buttons.**

**[INT-017] A ride that belongs to another driver can be taken over with ASSIGN TO SELF and CONFIRM, and CANCEL leaves it where it was.**

**Skipped until the developer's fix:** *[INT-007]* ASSIGN TO DRIVER hands a waiting ride to Automation Driver 2 (the driver is not offered in the list today).

## Things that happen while the app is open, and the app's lifecycle (Phase T7)

**Operator and rider events** *(the second driver is the operator; only possible before the trip starts)*
- **[RT-001]** An operator cancels the ride while the driver is on Home — Home goes back to "no trips" within seconds.
- **[RT-004]** An operator completes it — same. **[RT-005]** An operator moves it to another driver — same.
- **[RT-003 / REQ-004]** The rider cancels an assigned ride — Home empties and Completed shows "Canceled By Guest (at hh:mm)".
- **[RT-006]** The QA driver signs in somewhere else — the app returns to the Login screen (about 20 seconds).
- **[INT-009]** A new ride raises the Requests badge on Home by itself. **[INT-010]** It appears at the top of the Requests tab by itself.
- **[INT-011]** The rider cancels a waiting ride — its card leaves the Requests tab and Completed shows "Canceled By Guest".
- **[INT-015 / INT-016]** Confirming a ride another driver just took, or the rider just cancelled, opens no trip and the app stays usable.

**Two rides** — **[HOME-004]** a second ride is accepted once the first trip has started: "Current Trip", "3 Stops to go", one route over both rides. **[TRIPV-010]** "Go here instead" asks CANCEL / CONFIRM, CANCEL changes nothing, CONFIRM makes that stop next. **[HOME-011]** cancelling one ride leaves "1 Stop to go".

**App lifecycle** — **[STATE-001]** close and reopen: CONTINUE TRIP and the same stops. **[STATE-002]** background for 8 seconds keeps the trip view. **[STATE-004]** offline shows "You are Offline", then "Back Online", stops intact. **[STATE-006]** rotating keeps portrait. **[STATE-003]** sign out with a ride, sign in again, the ride is back on Home. **[HOME-009]** a ride assigned by another driver appears on Home within seconds.

**Not possible with our accounts:** operator or rider actions during a running trip (RT-002 and the trip-view versions of RT-003, 004, 005).

## Last open items (Phase T7, part 4)

**[ARR-015 / ARR-016] Two riders at one stop are both listed and counted, and the stops are then visited in order until the trip is completed** — seats 3, then 5; GO TO NEXT STOP waits for both riders; Mannheim Rd, then Door 3 with COMPLETE TRIP.

**[ARR-012] A second party that would take the shuttle over its 15 seats is refused when it is handed over, and the trip is unchanged** — 8 + 8 guests.

**[INT-022] The largest party the rider app allows (75) is shown correctly, and one more (76) is refused by the Rider API.**

**[INT-023] An off-duty driver can see and take a waiting ride, and starting the trip puts the driver back on duty.**

## Location during a trip (Phase T8)

**[GPS-001] While a trip runs, the app keeps a foreground location service with its notification.**

**[TRIPV-008 / GPS-004] When the device's location is moved next to the pickup, the app arrives by itself within seconds** — "Arrived At Terminal 5E" opens without a tap (5 to 11 seconds on staging).

**[TRIPV-008b] With the device still next to the pickup, going Back from the arrived screen brings the arrived screen up again** *(observed behaviour, possibly a defect)*.

**[TRIPV-009] While the device stays far from the pickup, the app does not arrive by itself** — still on the trip view after 45 seconds, estimates in hours.

**[GPS-003] Switching the device's location off during a trip makes the app ask for GPS access** — "Provide GPS Access."

## Not automated (decisions)

- Driver-app-disabled dialog (AUTH-009): no disabled account will be provided.
- First login, create passcode and reset passcode (PASS-004, PASS-005, AUTH-008): corner scenarios.
- Forced logouts by the server, another device or an expired token (SES-005 to SES-007).
