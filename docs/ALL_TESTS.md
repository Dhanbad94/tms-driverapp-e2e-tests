# All automated tests (158)

Collected from the test source on 2026-11-23. Repository: `tms-driverapp-e2e-tests`.

| Kind | Tests | Notes |
|---|---|---|
| Device tests (emulator, staging) | 115 | **74 in the smoke suite** (`smokeTest`), 111 in the regression (`regressionTest`, Tue/Thu), 3 skipped, 1 manual only (real SMS) |
| No-device tests (stub servers) | 43 | run on every push with `./gradlew test` |
| **Total** | **158** | exploration helpers are not counted (they assert nothing) |


## Launch smoke

| # | IDs | Test | Suites |
|---|---|---|---|
| 1 | AUTH-001 | A first launch with no saved session opens the Login screen | Smoke + regression |

## Login, session, shell, profile, OTP

| # | IDs | Test | Suites |
|---|---|---|---|
| 2 | AUTH-003 | Tapping the phone number area opens the login form | Smoke + regression |
| 3 | AUTH-004 | Submit is enabled only when a full phone number has been typed | Smoke + regression |
| 4 | AUTH-005 | The phone number field stops accepting digits after 15 | Smoke + regression |
| 5 | AUTH-006 | An unregistered phone number is rejected with a clear message | Smoke + regression |
| 6 | AUTH-010 | Choosing a country in the country picker updates the dial code | Smoke + regression |
| 7 | AUTH-012 | Submitting the phone number while offline shows a connectivity message | Regression only |
| 8 | PASS-001 | A driver with the correct passcode reaches the home screen | Smoke + regression |
| 9 | PASS-002 | A wrong passcode is rejected with a clear message | Smoke + regression |
| 10 | PASS-003 | Submit is available only once exactly four passcode digits are entered | Smoke + regression |
| 11 | PASS-006 | The passcode is hidden while it is being typed | Smoke + regression |
| 12 | SES-001 | A logged-in driver stays logged in after the app is closed and reopened | Smoke + regression |
| 13 | SES-002 / SES-004 | Signing out from Profile asks for confirmation and fully ends the session | Smoke + regression |
| 14 | SES-003 / SH-003 / SH-004 | Switching duty on and off shows or hides the toolbar Sign Out button | Regression only |
| 15 | SES-003b | The toolbar Sign Out button logs the driver out immediately, with no confirmation | Regression only |
| 16 | SH-001 / SH-002 | The home screen greets the driver and the bottom tabs open their screens | Smoke + regression |
| 17 | SH-009 | Losing the network shows an offline banner and the app recovers when it returns | Regression only |
| 18 | PR-001 / PR-002 | Profile shows the driver's details and Edit Profile rejects missing or invalid fields | Smoke + regression |
| 19 | CP-001 / CP-002 | Change Passcode rejects a wrong current passcode and a new passcode that does not match | Smoke + regression |
| 20 | OTP-001 / OTP-002 / OTP-004 / OTP-005 | The OTP screen shows the right titles, needs six digits, rejects a wrong code and can resend | Manual only (real SMS) |

## T1 Home with a ride

| # | IDs | Test | Suites |
|---|---|---|---|
| 21 | TRIP-001 | A ride created through the Rider API and assigned through the Driver API appears on the driver's home screen | Smoke + regression |
| 22 | HOME-005 | With no ride assigned the Home screen says there are no trips | Smoke + regression |
| 23 | HOME-001 / HOME-006 / HOME-007 / HOME-008 / HOME-010 / HOME-012 | A driver with one ride sees it, can refresh and restart, cannot go off duty, and sees it vanish when cancelled | Smoke + regression |

## T2 Start trip and shuttle

| # | IDs | Test | Suites |
|---|---|---|---|
| 24 | START-001 / 002 / 011 / 012 / 015 | START TRIP asks to confirm the shuttle once, opens one trip view, and CONTINUE TRIP returns to it without asking again | Smoke + regression |
| 25 | START-004 / 005 / 006 / 007 | Choosing a different shuttle lists the ride's needs and warns about an unsuitable shuttle | Smoke + regression |
| 26 | START-009 | With the device's location switched off the trip does not start and the driver is sent to Location settings | Regression only |
| 27 | START-010 | Starting the trip while offline does not open the trip, and starting again once back online does | Regression only |

## T3 Trip view and arriving

| # | IDs | Test | Suites |
|---|---|---|---|
| 28 | TRIPV-001 / 002 / 003 / 004 / 005 / 011 | The trip view lists the pickup and drop-off with their riders, switches between list and map, refreshes and shows rider details | Smoke + regression |
| 29 | TRIPV-006 / 007 | I've Arrived opens one 'Arrived At' screen for the pickup, and Back returns to the trip view and then Home | Smoke + regression |

## T4 Reach destination

| # | IDs | Test | Suites |
|---|---|---|---|
| 30 | ARR-001 / 002 / 003 / 004 / 005 / 006 / 008 / 009 / 017 | A driver picks the rider up, moves to the drop-off and completes the trip | Smoke + regression |
| 31 | ARR-007 / ARR-019 | Marking the only rider a no-show completes the trip, and REVIEW opens the Ride Requests screen | Smoke + regression |
| 32 | ARR-014 | GO TO NEXT STOP while offline keeps the screen and the choice, and works once the network is back | Regression only |
| 33 | ARR-013 | At the last stop the trip finishes by itself after about 10 seconds if the driver does nothing | Smoke + regression |
| 34 | ARR-018 | After a completed trip a new ride starts fresh, with no seats left over from the last one | Smoke + regression |

## T5 Requests tabs and card actions

| # | IDs | Test | Suites |
|---|---|---|---|
| 35 | REQ-005 | The Assigned and In-progress tabs say so when nothing is assigned | Smoke + regression |
| 36 | REQ-001 / REQ-007 / PAX-002 | The Assigned tab shows the ride with its details and a menu that opens the dialer | Smoke + regression |
| 37 | REQ-002 | The In-progress tab shows a ride once the trip has started | Smoke + regression |
| 38 | PAX-005 / REQ-003 / REQ-007 | Mark as Completed finishes the ride at once and lists it under Completed | Smoke + regression |
| 39 | PAX-006 / REQ-003 | Cancel Dispatch asks for a reason and then shows the ride as cancelled by the driver | Smoke + regression |
| 40 | PAX-004 / REQ-007 | Move to Request Queue puts the ride back among the unassigned requests | Smoke + regression |
| 41 | PAX-001 | The Assigned card shows the room number, the flight number and the note | Smoke + regression |
| 42 | PAX-001 | The rider details sheet shows the room number and the flight number | Skipped: the rider details sheet in the trip view does not show the room and flight numbers although the ride has them (the Requests card does). Enable when fi |
| 43 | PAX-007 | Cancelling with the reason 'Other' asks for text, and DONE stays off until some is typed | Smoke + regression |
| 44 | REQ-006 | A finished ride on the Completed tab offers only the rider's number in its menu | Smoke + regression |
| 45 | PAX-005 extra | Once the trip has started, the card menu no longer offers Move to Request Queue | Smoke + regression |

## T5 Rider sheet actions

| # | IDs | Test | Suites |
|---|---|---|---|
| 46 | PAX-010 | The rider menu offers Move to Request Queue, Change Drop-off, Assign to another Driver and the number, and nothing else | Smoke + regression |
| 47 | PAX-008 | Change Drop-off lists the current stops, can be cancelled, and moves the drop-off to the stop the driver picks | Smoke + regression |
| 48 | PAX-003 | PICK UP from the rider sheet needs a second tap to confirm | Smoke + regression |
| 49 | PAX-009 | ASSIGN TO DRIVER opens the Assign Driver screen with the available drivers, and leaving it keeps the ride with this driver | Smoke + regression |
| 50 | PAX-009 | A ride assigned to another driver leaves this driver and shows up for the other one | Skipped: the Assign Driver list does not offer 'Automation Driver 2' (id 3355) even with the account on duty and (reported) open in another app, so a ride cann |

## T6 Taking a ride from the queue

| # | IDs | Test | Suites |
|---|---|---|---|
| 51 | INT-001 / 002 / 003 / 004 | A waiting ride is listed with its details and the two assign buttons | Smoke + regression |
| 52 | INT-018 | The sort sheet offers Oldest First, Newest First and Rider Name, and reorders the list | Smoke + regression |
| 53 | INT-019 | Search needs two characters, finds the ride by its name and says 'No Results Found' otherwise | Smoke + regression |
| 54 | INT-012 / INT-006 | ASSIGN TO SELF stages the ride with a CONFIRM & START TRIP bar, and CANCEL assigns nothing | Smoke + regression |
| 55 | INT-005 / INT-006 | Confirming ASSIGN TO SELF assigns the ride, starts the trip and opens the trip view | Smoke + regression |
| 56 | INT-013 | With location services off, assigning a ride asks for GPS access and assigns nothing | Regression only |
| 57 | INT-014 | A party larger than the shuttle's 15 seats cannot be assigned | Smoke + regression |
| 58 | INT-021 | A long rider name with accents and punctuation is shown without hiding the assign buttons | Smoke + regression |
| 59 | INT-007 | ASSIGN TO DRIVER hands a waiting ride to another driver, and it leaves the queue | Skipped: the Assign Driver list does not offer 'Automation Driver 2' (id 3355) even with the account on duty and (reported) open in another app, so a ride cann |
| 60 | INT-022 | The largest party the rider app allows (75) is shown correctly, and one more (76) is refused by the Rider API | Smoke + regression |
| 61 | INT-023 | An off-duty driver can see and take a waiting ride, and starting the trip puts the driver back on duty | Regression only |
| 62 | INT-017 | A ride that belongs to another driver can be taken over with ASSIGN TO SELF and CONFIRM, and CANCEL leaves it where it was | Regression only |

## T7 Operator and rider events

| # | IDs | Test | Suites |
|---|---|---|---|
| 63 | RT-001 | When an operator cancels the ride while the driver is on Home, the ride disappears and Home shows no trip | Regression only |
| 64 | RT-004 | When an operator completes the ride while the driver is on Home, Home goes back to 'no trips' | Regression only |
| 65 | RT-005 | When an operator moves the ride to another driver while the driver is on Home, Home goes back to 'no trips' | Regression only |
| 66 | RT-003 / REQ-004 | When the rider cancels an assigned ride, Home goes back to 'no trips' and the ride shows as cancelled by the guest | Regression only |
| 67 | INT-015 | Confirming a ride that another driver has just taken does not assign it to the QA driver | Regression only |
| 68 | INT-016 | Confirming a ride that the rider has just cancelled does not assign it and does not crash the app | Regression only |
| 69 | INT-009 | A ride that arrives while the driver is on Home raises the Requests badge without a reload | Regression only |
| 70 | INT-010 | A ride that arrives while the driver is on the Requests tab appears in the list without a reload | Regression only |
| 71 | INT-011 / REQ-004 | When the rider cancels a waiting ride it leaves the Requests tab and shows as cancelled by the guest | Regression only |
| 72 | RT-006 | When the QA driver signs in somewhere else, the app is signed out and shows the Login screen | Regression only |

## T7 Two rides

| # | IDs | Test | Suites |
|---|---|---|---|
| 73 | HOME-004 | A second ride can be added once the trip has started; Home then counts the stops of both rides | Smoke + regression |
| 74 | TRIPV-010 | With two rides, 'Go here instead' on a later stop asks for confirmation; CANCEL changes nothing and CONFIRM makes it the next stop | Smoke + regression |
| 75 | HOME-011 | When one of two rides is cancelled, Home counts only the stops left | Regression only |

## T7 App lifecycle

| # | IDs | Test | Suites |
|---|---|---|---|
| 76 | STATE-001 | Closing the app during a trip and opening it again resumes the trip | Smoke + regression |
| 77 | STATE-002 | Sending the app to the background and back keeps the trip view as it was | Smoke + regression |
| 78 | STATE-004 | Going offline during a trip shows the offline banner, and the trip view is intact when the network returns | Regression only |
| 79 | STATE-006 | Turning the device sideways does not turn the app: it stays in portrait | Smoke + regression |
| 80 | STATE-003 | Signing out while a ride is assigned: the ride stays assigned and is back on Home after signing in again | Smoke + regression |
| 81 | HOME-009 | A ride that another driver assigns while the driver is on Home appears there by itself | Regression only |

## T7 Two riders at one stop

| # | IDs | Test | Suites |
|---|---|---|---|
| 82 | ARR-015 / ARR-016 | Two riders at one stop are both listed and counted, and the stops are then visited in order until the trip is completed | Regression only |
| 83 | ARR-012 | A second party that would take the shuttle over its 15 seats is refused when it is handed over, and the trip is unchanged | Regression only |

## T8 Location

| # | IDs | Test | Suites |
|---|---|---|---|
| 84 | GPS-001 | While a trip runs, the app keeps a foreground location service with its notification | Smoke + regression |
| 85 | TRIPV-008 / GPS-004 | When the device's location is moved next to the pickup, the app arrives by itself within seconds | Regression only |
| 86 | TRIPV-008b | While the shuttle is inside the pickup's geofence, going Back from the arrived screen brings the arrived screen up again | Regression only |
| 87 | TRIPV-009 | While the device stays far from the pickup, the app does not arrive by itself | Smoke + regression |
| 88 | GPS-003 | Switching the device's location off during a trip makes the app ask for GPS access | Regression only |

## T9 Shuttle

| # | IDs | Test | Suites |
|---|---|---|---|
| 89 | SHUT-001 / SHUT-002 / SHUT-004 / SHUT-009 | Home shows the organisation and shuttle; tapping the shuttle lists both shuttles with seats and driver, and CONFIRM is already on | Smoke + regression |
| 90 | SHUT-003 | Confirming the current shuttle returns Home with the same shuttle | Smoke + regression |
| 91 | SHUT-005 / SHUT-006 | Search narrows the shuttles by part of the name in any case, and says 'No match found' otherwise | Smoke + regression |
| 92 | SHUT-007 | A shuttle held by another driver asks 'Belongs to another driver'; PICK ANOTHER keeps the current shuttle | Smoke + regression |
| 93 | SHUT-010 / SHUT-011 / SHUT-012 / SHUT-013 | Rename Shuttle opens with the current name, refuses a one-letter name, stops at 30 characters and keeps CHANGE off for a blank or unchanged name | Regression only |
| 94 | SHUT-014 | Renaming the shuttle shows a confirmation and the new name; renaming it back restores it | Regression only |
| 95 | SHUT-015 | Renaming while offline does not rename: the dialog stays open and the name is unchanged once online again | Regression only |

## T10 Help Center

| # | IDs | Test | Suites |
|---|---|---|---|
| 96 | HELP-001 | The Help tab opens 'Help Center' with the video tour and three cards | Smoke + regression |
| 97 | HELP-002 / HELP-003 | Call Support shows the support number, and CALL NOW opens the dialer with that number | Smoke + regression |
| 98 | HELP-004 | Launch Quick Tour shows pictures that move on with each tap, and returns to the Help Center after the last one | Smoke + regression |
| 99 | HELP-005 | Pressing Back during the quick tour closes it | Smoke + regression |
| 100 | HELP-006 | Animation Tour steps through its pages with NEXT and BACK, and NEXT is off on the last page | Smoke + regression |
| 101 | HELP-007 | Without a network the Help Center and the Support screen still open | Regression only |

## T11 Navigation

| # | IDs | Test | Suites |
|---|---|---|---|
| 102 | NOTIF-001 | The bottom bar offers exactly Home, Profile and Help; there is no Notifications tab anywhere | Smoke + regression |
| 103 | NOTIF-002 / LOG-001 | Nothing on Home, Ride Requests, Profile or Help leads to Notifications or a Dispatch Log | Smoke + regression |

## T12 Map tab and Change Drop-off

| # | IDs | Test | Suites |
|---|---|---|---|
| 104 | MAP-001 / MAP-004 | The Map tab shows the destination card, and I've Arrived on it opens the Arrived screen at once | Smoke + regression |
| 105 | MAP-003 | Tapping the rider on the Map tab opens the rider sheet | Smoke + regression |
| 106 | MAP-005 | Dragging the map shows RE-CENTER, and tapping it hides the button again | Smoke + regression |
| 107 | DROP-001 / DROP-002 | Change Drop-off opens with UPDATE off, says 'No match found' for a bad search, and turns UPDATE on once a stop is chosen | Smoke + regression |
| 108 | DROP-003 | Opening Change Drop-off without a network drops the driver on Home, with only the offline banner as explanation | Regression only |
| 109 | DROP-004 | Pressing UPDATE without a network changes nothing and gives no message (recorded) | Regression only |

## T12 Operator on the trip view

| # | IDs | Test | Suites |
|---|---|---|---|
| 110 | DISP-001 | When the operator cancels ride B, the route drops its stop within seconds and no 'Dispatch canceled' sheet appears | Regression only |
| 111 | DISP-003 | When the operator completes ride B, the route drops its stop and no 'Dispatch completed' sheet appears | Regression only |
| 112 | DISP-004 | When the operator moves ride B to another driver, the route drops its stop and no 'Dispatch has changed' sheet appears | Regression only |

## T12 Profile photo

| # | IDs | Test | Suites |
|---|---|---|---|
| 113 | PROF-001 | The camera icon on Edit Profile opens a sheet with Camera and Gallery only, and Back closes it | Smoke + regression |
| 114 | PROF-002 | Refusing the camera permission does nothing and says nothing | Smoke + regression |
| 115 | PROF-003 | Delete Account asks twice; answering NO keeps the account and the driver signed in | Smoke + regression |

## Configuration checks

| # | IDs | Test | Suites |
|---|---|---|---|
| 116 | - | staging resolves the suffixed package and splash activity | No device (every push) |
| 117 | - | development resolves its package | No device (every push) |
| 118 | - | production flavor is rejected | No device (every push) |
| 119 | - | unknown environment file fails clearly | No device (every push) |
| 120 | - | resource ids use the flavor application id | No device (every push) |

## Driver API client (stub server)

| # | IDs | Test | Suites |
|---|---|---|---|
| 121 | - | A dispatcher with the right gateway credential and passcode can log in | No device (every push) |
| 122 | - | A wrong gateway credential is reported as 401 even though the HTTP status is 200 | No device (every push) |
| 123 | - | A missing gateway credential is reported as 401 | No device (every push) |
| 124 | - | A wrong passcode is reported as 400 | No device (every push) |
| 125 | - | A ride waiting in the queue is found by its rider name | No device (every push) |
| 126 | - | Waiting for a ride that never appears fails with a clear message | No device (every push) |
| 127 | - | Assigning a queued ride to a driver succeeds and removes it from the queue | No device (every push) |
| 128 | - | Assigning a ride that does not exist is reported as failed (code 400) | No device (every push) |
| 129 | - | An assigned ride can be completed or cancelled with a readable confirmation message | No device (every push) |
| 130 | - | A dispatch action on an unknown dispatch is rejected with code 400 | No device (every push) |
| 131 | - | Proceeding after an assignment starts the trip (trip_started goes from 0 to 1) | No device (every push) |
| 132 | - | A ride still waiting in the queue is cancelled with the queue call, an assigned ride with the dispatch call | No device (every push) |
| 133 | - | The dashboard reports how many dispatches the driver has (0, then 1 after an assignment) | No device (every push) |
| 134 | - | The driver id is read from the login token | No device (every push) |
| 135 | - | Cleanup never throws even when the ride is already gone | No device (every push) |
| 136 | - | Production and preproduction hosts are refused, staging is accepted | No device (every push) |
| 137 | - | Credentials never appear when the connection settings are logged | No device (every push) |

## Rider API client and ride data (stub server)

| # | IDs | Test | Suites |
|---|---|---|---|
| 138 | - | The supplied stop list loads with ten stops, each with an id, name and coordinates | No device (every push) |
| 139 | - | A random pickup and drop-off pair always uses two different stops | No device (every push) |
| 140 | - | A new ride keeps the template's rider details and gets the unique name that was asked for | No device (every push) |
| 141 | - | The request JSON has the riders and both stops, and keeps flight and room as null | No device (every push) |
| 142 | - | A ride without a note leaves the note out of the request instead of sending null (staging rejects null) | No device (every push) |
| 143 | - | A guest token is obtained with the gateway credential and used to create the ride | No device (every push) |
| 144 | - | A ready Bearer token is used directly, without calling the guest-user endpoint | No device (every push) |
| 145 | - | A wrong gateway credential is reported as 401 even though the HTTP status is 200 | No device (every push) |
| 146 | - | A wrong organisation token-id is rejected with 400 | No device (every push) |
| 147 | - | A ride with the same pickup and drop-off is rejected with 400 | No device (every push) |
| 148 | - | A ride created through the Rider API shows up in the Driver API queue and can be assigned | No device (every push) |
| 149 | - | A production rider API host is refused and credentials never appear in the settings text | No device (every push) |
| 150 | - | The rider can cancel a ride with its ride code and a reason, and a missing reason is refused | No device (every push) |

## Ride fixture and cleanup (stub servers)

| # | IDs | Test | Suites |
|---|---|---|---|
| 151 | - | An assigned ride is created with the requested party size and ADA flag, found in the queue and assigned | No device (every push) |
| 152 | - | Cleanup cancels every ride the test created and leaves the driver with no active ride | No device (every push) |
| 153 | - | Cleanup also removes a ride that was created but never assigned | No device (every push) |
| 154 | - | Rides left on the driver by an earlier run are cleared before the test starts | No device (every push) |
| 155 | - | If a ride cannot be cancelled, cleanup fails loudly instead of leaving it behind quietly | No device (every push) |
| 156 | - | A ride that was picked up cannot be cancelled, so cleanup finishes the trip instead | No device (every push) |
| 157 | - | A picked-up ride left over from an earlier run is cleared before the test starts | No device (every push) |
| 158 | - | Every ride gets a different rider name | No device (every push) |
