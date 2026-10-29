# Additional coverage plan: what the ASAP-mode source still offers (Phases T9 to T12)

Status: **Phases T9 to T12 are done and passing (2026-10-28/29), except T12d (tracker-state combinations, to be checked against T2) and T12f (View Timeline and Select Organisation, to be read).** Decisions taken: renaming and restoring shuttles is allowed, Shuttle-2 is the "other" shuttle, and video playback is skipped.
Source: a read of the on-demand (ASAP) code of the Driver app (`app/src/main/java/com/trackmyshuttle/driver` and `res`), 2026-10-28.
The statements about the source below come from that read and from spot checks of the three biggest claims (Dispatch Log has no entry point, the Notifications tab is hidden, Help opens a Compose screen). Anything marked **CONFIRM** must be checked on the running app before an assertion is written.

## 1. What the source read changed

| Finding | Effect on the plan |
|---|---|
| `SideMenuFragment` (name, shuttle, organisation, version, Terms, Log out) is **not hosted anywhere**: no drawer, no button opens it. The real ASAP home header (`tv_org`, `tv_shuttle`, duty switch, Sign Out) does the same jobs. | The side menu is **not tested** as a screen. Its jobs are covered on Home (Phase T9). |
| `DISPATCH_LOG_FRAGMENT` is handled in `LandingActivity` but **no code sends it**. There is no button, menu or bottom-bar item. | The Dispatch Log **cannot be reached by a driver**. Not automatable; raised as a question (section 6). |
| The **Notifications** bottom-bar item has `visibility="gone"`, and the screen shows an "under development" picture over its list. | Only a negative check (the tab is not offered). Raised as a question. |
| **Help** opens a Compose screen ("Help Center") with a video tour, Launch Quick Tour, Animation Tour and Call Support. Compose has no resource ids or test tags. | Phase T10, located by text. |
| **Select Shuttle** (opened from the Home header) has search, capacity and ADA rules, tracker-state dialogs and a **Rename Shuttle** dialog. | Phase T9. |
| The Help row of the side menu is wired to Change Passcode, `ChangeShuttleFragment` is never navigated to, and `dispatch_status` has no text binding. | Not tests: **findings for the developer** (section 6). |

## 2. Phase T9: Home header and Select Shuttle (QA driver only)

Reached from Home: tap `tv_shuttle` (opens **Select Shuttle**, title "Select Shuttle"). `tv_org` only acts when the account has several organisations (`multi_org`; the QA account is single-organisation, so it is checked for being inert only).

| ID | Type | Scenario | Setup | Expected | Evidence | Pri |
|---|---|---|---|---|---|---|
| SHUT-001 | P | The Home header shows the organisation and the current shuttle | S0, shuttle chosen | `tv_org` "Automated OD ASAP QA", `tv_shuttle` "Automated Shuttle-1" | LIVE (seen) | P1 |
| SHUT-002 | P | Tapping the shuttle opens the Select Shuttle list | S0 | Title "Select Shuttle", a row per shuttle with name, seats, ADA icon, driver name (if taken), tracker state | CODE | P1 |
| SHUT-003 | P | Choosing another shuttle enables CONFIRM, and CONFIRM returns Home with the new shuttle | S0, two shuttles | Button reads CONFIRM (not "start trip" off a trip), Home header shows the new name; **restore the original shuttle afterwards** | CODE | P0 |
| SHUT-004 | N | CONFIRM stays off until a row is chosen | S0 | CONFIRM disabled | CODE | P1 |
| SHUT-005 | P | Search filters by part of the name, any case, no minimum length | S0 | Typing "shuttle-2" leaves only matching rows | CODE | P2 |
| SHUT-006 | N | Search with no match says so | S0 | "No match found" | CODE | P2 |
| SHUT-007 | C | A shuttle that another driver is using cannot be taken | the second driver holds a shuttle | A tracker-state dialog explains (texts **CONFIRM**); the shuttle stays unchanged | CODE; needs setup | P2 |
| SHUT-008 | C | Changing shuttle while a trip is running | S1, started | Server decides (**CONFIRM**): record the message; the trip must stay intact | CODE; server rule unknown | P2 |
| SHUT-009 | C | The Home header is inert for the organisation on a single-organisation account | S0 | `tv_org` does not open a list | CODE | P3 |
| SHUT-010 | P | The pencil on a row opens **Rename Shuttle** with the current name filled in | S0 | Title "Rename Shuttle", name field, Cancel and Change | CODE | P1 |
| SHUT-011 | N | A name shorter than 2 characters is refused | rename dialog | "Please enter valid name"; nothing is sent | CODE | P1 |
| SHUT-012 | N | The name is limited to 30 characters and emojis are refused | rename dialog | Longer text is cut at 30; emoji not accepted | CODE | P2 |
| SHUT-013 | N | Change is off for a blank name or an unchanged name | rename dialog | Disabled after editing to blank or back to the original | CODE | P2 |
| SHUT-014 | P | Renaming works and shows a confirmation | rename dialog | Toast "Shuttle name changed successfully!!", the list shows the new name; **the name is restored in the same test** | CODE; changes shared data | P1 |
| SHUT-015 | C | Rename while offline | rename dialog, airplane mode | Connectivity message, dialog stays, nothing changes | CODE | P3 |

### T9 result (live, staging, emulator)
7 tests, 11 of the 15 scenarios, all passed in one run (`ShuttleTest`).

| Scenarios | Result |
|---|---|
| SHUT-001, 002, 004, 009 | Done. Home shows "Automated OD ASAP QA" and "Automated Shuttle-1"; the organisation is **not enabled** (single-organisation account); the list shows both shuttles with seats (15 and 10), the driver using each ("Automation Driver" and "Automation Driver 2") and a check mark on the current one; CONFIRM is on at once because the current shuttle is already selected (so SHUT-004 "CONFIRM off until a row is chosen" does not apply). |
| SHUT-003 | Narrowed: CONFIRM on the current shuttle returns Home with the same shuttle. A different shuttle cannot be taken (see SHUT-007). |
| SHUT-005, 006 | Done. Search is by part of the name, any case, no minimum length; no match shows "No match found"; Close restores both. |
| SHUT-007 | Done without any setup: **Shuttle-2 is held by Automation Driver 2**, so choosing it shows "Belongs to another driver / This shuttle is assigned to: / Automation Driver 2" with SELECT ANYWAY and PICK ANOTHER. Only PICK ANOTHER is tapped; SELECT ANYWAY would take the shuttle from that driver. |
| SHUT-008 | Not applicable here: there is no free second shuttle to change to. |
| SHUT-010 to 013 | Done. The dialog opens with the current name and CHANGE off; blank or unchanged keeps it off; a one-letter name gives "Please enter valid name" (the text has a trailing space); 40 typed characters are cut to 30. The emoji filter is not tested (cannot be typed through the driver). |
| SHUT-014 | Done. Renaming shows the toast "Shuttle name changed successfully!!" and the new name; the test renames it back in a `finally` block and checks Home. |
| SHUT-015 | Done. Offline, CHANGE gives the toast "Connectivity Issue. Please check your internet connection and try again later" and the dialog stays open; the name is unchanged afterwards. |

Other facts seen: the list rows have **no ADA icon** (it seems to appear only when a ride's requirements are matched), and the tracker state of Shuttle-1 changes between "IGNITION OFF" and "RUNNING" with the emulator's location service, so it is not asserted.


## 3. Phase T10: Help Center (QA driver only)

Reached from the bottom bar "Help" (`cl_nav_help`). A Compose screen: located with text, not ids.

| ID | Type | Scenario | Expected | Evidence | Pri |
|---|---|---|---|---|---|
| HELP-001 | P | The Help tab opens "Help Center" with the video tour and three cards | Cards "Launch Quick Tour", "Animation Tour", "Call Support" with their sub-texts | CODE | P1 |
| HELP-002 | P | Call Support opens the support screen | "Need Help?", "Support Phone Number", the number, the tip about the organisation name, CALL NOW | CODE | P1 |
| HELP-003 | P | CALL NOW opens the phone dialer with the support number | Dialer shows the number (nothing is dialled); coming back returns to the app | CODE; dialer pattern known from T5 | P2 |
| HELP-004 | P | Launch Quick Tour shows the intro pictures and each tap moves on | Overlay appears, taps advance, the last tap closes it, Home returns | CODE; picture count **CONFIRM** | P1 |
| HELP-005 | N | Back during the quick tour closes it | Overlay gone | CODE | P2 |
| HELP-006 | P | Animation Tour steps through the walkthrough with Next and Back | Title and description change; Back on the first step does nothing or is off (**CONFIRM**) | CODE; pictures come from the server | P2 |
| HELP-007 | C | The Help content loads offline or fails cleanly | No crash; cards still shown or an error text (**CONFIRM**) | CODE | P3 |
| HELP-008 | C | The video tour card plays or shows its poster and controls | Poster with play; playback not asserted (depends on the network and the file) | CODE | P3 |

### T10 result (live, staging, emulator)
6 tests, 7 of the 8 scenarios, all passed in two consecutive runs (`HelpTest`). HELP-008 (video playback) is skipped as decided.

| Scenarios | Result |
|---|---|
| HELP-001 | Done. "Help Center", the Help tab selected, "Video Tour" with "Duration : 3 mins", and three cards with their explanations: Launch Quick Tour ("Take a guided tour of the app features"), Animation Tour ("Browse app screenshots and features"), Call Support ("Get immediate help from our support team"). |
| HELP-002, 003 | Done. The Support screen reads "Need Help?", "Our support team is available 24/7 to assist you", "Support Phone Number", "(888) 574-8885", CALL NOW and the tip about the organisation name. CALL NOW opens the Google dialer with 8885748885 (nothing is dialled); coming back returns to the Support screen. |
| HELP-004 | Done. Launch Quick Tour shows a full-screen picture; **7 taps** close it, back on the Help Center. |
| HELP-005 | Done. Back during the quick tour closes it and the Help Center is shown. |
| HELP-006 | Done. Animation Tour has **4 pages**: How to go On/Off Duty?, How to View Ride Requests?, How to Start Trip?, How to Reject/Manage Request?. NEXT is off on the last page; BACK goes to the previous page. |
| HELP-007 | Done. With the network off, the Help Center cards and the Support screen still open. |

The Help Center is a Compose screen: its texts are visible to the driver by text (no ids), the Support screen and the walkthrough have ids. The first-run tour (coachmark) stays unautomated: it is server state and shows once per app version.

The first-run tour (coachmark) is **server-state**: it shows once per app version until the server stores it. It is a corner scenario and is **not automated**; Launch Quick Tour reaches the same overlay on demand.

Estimated tests: 6 to 7.

## 4. Phase T11: Notifications and Dispatch Log (negative checks only)

| ID | Scenario | Expected | Notes |
|---|---|---|---|
| NOTIF-001 | The bottom bar offers only Home, Profile and Help | No Notifications item is visible | Probably already covered by the shell test; add an assertion if not |
| NOTIF-002 | The Home ride-request icon opens Ride Requests, not Notifications | Requests screen | Already covered in T5/T6 |
| LOG-001 | No control on Home, Profile or Help leads to a Dispatch Log | Not testable as a positive scenario | Waits for the product answer in section 6 |

### T11 result (live, staging, emulator)
2 tests (`NavigationTest`), both passed.

| Scenarios | Result |
|---|---|
| NOTIF-001 | Done. The bottom bar holds exactly **Home, Profile and Help** on Home, Profile and Help; the Notifications tab is not in the screen at all (no node, not even a hidden one). |
| NOTIF-002, LOG-001 | Done. No text on Home, Ride Requests, Profile or Help mentions Notifications or a Dispatch Log. The Home Requests button opens "Ride Requests". The toolbar icon on Ride Requests (`iv_menu`) is a **way back to Home**, not a menu. Home has a closable "new ride requests" banner (`iv_close`) and nothing else that navigates. |

These tests pass today because the screens are unreachable. If the product owner decides they should be reachable (question 1 and 2 in section 6), these are the tests that fail and become real scenarios (the source describes the Dispatch Log's list, its search from 2 characters, its confirm bar and its empty text "No Dispatches Found within last 24 hours").

## 5. Phase T12: the second source read (plan, nothing automated yet)

Source: a read of the ASAP-mode code for the map tab, stop info, Mark Arrived, the dispatch cancel/complete dialogs, tracker-state dialogs, Change Drop-off failures, the profile photo, the review sheets and everything else a driver can reach (2026-10-29). Statements come from that read and are marked **CONFIRM** where only the live app can settle them.

### 5.1 What the read found

| Area | Finding | Plan |
|---|---|---|
| **Dispatch cancel / complete dialogs** | Opened **only by socket alerts**, never by a tap, and only on the trip view, the Arrived screen, Change Drop-off and Assign Driver (not on Home, which is why the T7 tests saw no sheet). Texts: "Dispatch completed for:", "Dispatch canceled for:", "Dispatch has changed for:" (cancel dialog, shows the old and new stop, button OK, closes by itself after 6 seconds) and, when the last dispatch ends, "Operator marked dispatch complete.", "Your last dispatch got cancelled. Your trip is now complete.", "Operator assigned the dispatch to another driver. Your trip is now complete." (complete dialog, button "Back To Home"). | **Automatable** with the two-ride setup from T7: ride B has not started, so the second driver may cancel, complete or move it while the QA driver is on the trip view (T12a). |
| **Map tab** | A header card at the top (not a bottom card): destination, address, estimated time ("Arrived" under a minute, otherwise "N min" / "H hr M min"), rider chips, "I've Arrived", and a **Re-center** button that appears after the driver moves the map. Tapping a chip opens the rider sheet. | **Automatable** apart from the map picture itself (T12b). |
| **Stop info dialog** | Opens only by tapping a stop **marker on the Google map**; the map content is not in the accessibility tree. It shows "Go here now" only if the server sets `changeToNext`. | **Not automatable** from the UI tree. Listed for the developer. |
| **Mark Arrived dialog** | **Dead code**: nothing ever shows it. Arrival is "I've Arrived" (manual, no confirmation) or the automatic arrival under one minute (both call `mark-arrived`). | No test; documented. |
| **Tracker-state dialogs** | Seven single dialogs and an "Alerts" dialog with 11 combinations (unplugged, other driver, too small, ADA). "Belongs to another driver" is tested (T9); the others depend on the shuttle setup and the ride's needs. | T12d: **check what T2 already covers**, then add only the missing combinations. |
| **Change Drop-off errors** | UPDATE starts off and turns on once a stop is chosen (and never off again, even for the same stop as the pickup). Search has no minimum length; "No match found" for no match. **If the list cannot load, the app silently returns to the trip view; if UPDATE fails the error is ignored and the screen just stays: no message at all.** | T12c: tests that record this (possible defect, see section 6). |
| **Profile photo** | Edit Profile > camera icon > sheet with **Camera** and **Gallery** only (no remove option); the X on the sheet has no handler (dismiss by Back). Denied camera permission does nothing and says nothing. Crop screen "Crop Photo", 1:1, then compressed to 200x200. Size check "File size should be less than 1 Mb" (only triggers at 2 MB or more). | T12e: open the sheet and check the options; the photo upload itself is a stretch goal. |
| **Delete Account** | Edit Profile toolbar menu > "Delete Account" > alert "Delete Account?" with Yes and No. | T12e: open the alert and choose **No** only. |
| **Trip completed / review sheets** | Variants already seen in T4. Not seen: the plain "Trip completed" dialog (only when no request is waiting or the operator bypass is off) and the Review sheet's X (it closes like LATER). | The plain variant cannot be produced on the shared queue (always requests waiting). The X is a small add-on to T4's flow. |
| **Other reachable items** | Passenger info menu (covered), Confirm Shuttle dialog (covered in T2), Change Passcode (covered), View Timeline and the Select Organisation flow (not read in detail). | T12f: read View Timeline once more. |

### 5.2 Scenarios

| ID | Type | Scenario | Setup | Expected | Evidence | Pri | Part |
|---|---|---|---|---|---|---|---|
| DISP-001 | C | The operator cancels a not-yet-started ride while the driver is on the trip view | two rides, A started and B waiting, app on the trip view, 2nd driver cancels B | A bottom sheet "Dispatch canceled for:" with the old stop, OK, closing by itself after 6 seconds; the trip carries on with ride A | CODE | P1 | T12a |
| DISP-002 | C | The same while the driver is on the Arrived screen | as above, on the Arrived screen | Same sheet, Arrived screen refreshed | CODE | P2 | T12a |
| DISP-003 | C | The operator completes ride B | as DISP-001, 2nd driver completes B | "Dispatch completed for:" sheet | CODE | P2 | T12a |
| DISP-004 | C | The operator moves ride B to another driver | as DISP-001, `change-driver` | "Dispatch has changed for:" sheet with the old and new stop | CODE | P2 | T12a |
| DISP-005 | C | OK closes the sheet at once | DISP-001 | Sheet gone before 6 seconds | CODE | P3 | T12a |
| DISP-006 | C | The last ride ending shows "Your trip is now complete" and Back To Home | needs the **last** dispatch to end by the operator | Not reachable: the running ride cannot be ended by the operator | CODE | – | blocked |
| MAP-001 | P | The Map tab shows the destination card: stop name, address, estimated time and "I've Arrived" | S1, started | Card texts as the list; the list cards are gone | CODE; T3 covered the name | P1 | T12b |
| MAP-002 | P | The estimated time reads "H hr M min" far away and "Arrived" next to the stop | S1, started, GPS far then at the stop | Texts change | CODE; location helper exists (T8) | P2 | T12b |
| MAP-003 | P | Tapping a rider chip on the Map tab opens the rider sheet | S1, started, Map tab | Sheet with name and party | CODE | P2 | T12b |
| MAP-004 | P | "I've Arrived" on the Map tab opens the Arrived screen at once, without a confirmation | S1, started, Map tab | Arrived screen for the pickup | CODE | P1 | T12b |
| MAP-005 | C | After the driver drags the map, Re-center appears; tapping it hides it | S1, started, swipe on the map | Button appears then goes | CODE; gesture on a map (**CONFIRM**) | P3 | T12b |
| DROP-001 | N | UPDATE is off when Change Drop-off opens | S1 started | Disabled until a stop is picked | LIVE (button seen) | P1 | T12c |
| DROP-002 | N | A search with no match says "No match found" | Change Drop-off | Text shown, UPDATE still off | CODE | P2 | T12c |
| DROP-003 | C | Opening Change Drop-off offline: the list cannot load | S1, airplane mode | The app returns to the trip view with no message | CODE | P3 | T12c |
| DROP-004 | C | UPDATE offline: nothing happens and no message | S1, stop chosen, offline | Screen stays, no toast (records the missing message) | CODE | P3 | T12c |
| TRK-001 | C | Choosing a shuttle that is both held by another driver and too small / not ADA shows the "Alerts" list | S1 start trip with a 12-guest ADA ride, choose Shuttle-2 | Alerts: Belongs to another driver, Shuttle too small, Requires ADA | CODE; **check T2 first** | P2 | T12d |
| PROF-001 | P | The camera icon on Edit Profile opens a sheet with Camera and Gallery only | S0 | Two options, no remove option; Back closes it | CODE | P2 | T12e |
| PROF-002 | C | Denied camera permission does nothing and says nothing | permission revoked | No crash, no message | CODE | P3 | T12e |
| PROF-003 | N | Delete Account asks twice and No keeps the account | S0 | Sheet, then "Delete Account?" with Yes and No; No leaves the driver signed in | CODE | P1 | T12e |
| PROF-004 | P | Gallery > crop > save changes the photo | image pushed to the emulator | Stretch goal: the Android photo picker is slow and fragile | CODE | P3 | T12e (stretch) |

### 5.4 T12 result (live, staging, emulator, 2026-10-29)
14 new tests in three classes, all passed (`MapAndDropoffTest` 6, `OperatorOnTripViewTest` 3, `ProfilePhotoTest` 3, plus the page objects they share). The Driver 2 session-out during the operator test was accepted by the user.

| Scenarios | Result |
|---|---|
| **MAP-001, 004** | Done. The Map tab card shows the stop, its address, an estimate in hours ("123 hr 56 min" far away), the rider chip with the party size and "I've Arrived"; the list cards are gone. "I've Arrived" on the map opens the Arrived screen at once, without a confirmation. |
| MAP-003 | Done. Tapping the rider on the card opens the rider sheet (name, guests, stops, note, PICK UP). |
| MAP-005 | Done. Dragging the map shows RE-CENTER; tapping it hides the button. |
| MAP-002 | Not tested: when the estimate reads "Arrived" the app already leaves for the Arrived screen. |
| **DROP-001, 002** | Done. UPDATE is off when Change Drop-off opens and when a search finds nothing ("No match found"); choosing a stop turns it on; CANCEL returns to the trip view unchanged. A second search reuses the open search box. |
| **DROP-003** | Done. With no network, opening Change Drop-off drops the driver on **Home** ("Current Trip", CONTINUE TRIP) with only the "You are Offline" banner and no message. |
| **DROP-004** | Done. With no network, UPDATE leaves the screen on Change Drop-off with "You are Offline" and **no message**; the trip keeps its old drop-off. (The silent failure is a question for the developers.) |
| **DISP-001, 003, 004** | Done, with a different outcome than the source suggested. When the second driver cancels, completes or moves ride B while the driver is on the trip view, **no "Dispatch canceled / completed / has changed" sheet appears on staging**: the route just drops ride B's stop within 0 to 1 seconds. The sheets are only shown for socket alerts that the server did not send for these driver-API actions (it may send them for actions from the dispatcher console). The tests fail if a sheet starts to appear. |
| DISP-002, 005 | Not produced (no sheet to test on the Arrived screen or to close). |
| DISP-006 | Blocked: the operator cannot end the already-started last ride. |
| **PROF-001** | Done. The camera icon opens a sheet with exactly Camera and Gallery; Back closes it. |
| **PROF-002** | Done. With the camera permission taken away (which **restarts the app**) the Camera option shows the system dialog "Allow ... to take pictures and record video?" with While using the app / Only this time / Don't allow; Don't allow returns to Edit Profile with no message. |
| **PROF-003** | Done, never deleting. The Edit Profile toolbar menu offers "Delete Account"; the alert reads "Delete Account? Are you sure you want to delete your account?" with "YES, DELETE" and "NO"; NO closes the alert and the driver stays signed in. |
| PROF-004 | Not done (stretch): gallery upload through the Android photo picker. |
| Hand-over rule | ride B can only be handed over with the **same pickup** as ride A's running trip; a different pickup answered 500 "Something went wrong". |

### 5.3 Parts and size

| Part | What | Tests (est.) | Needs |
|---|---|---|---|
| **T12a** | Dispatch cancel / complete / changed sheets on the trip view and the Arrived screen | 3 to 4 | Driver 2 as operator (signs out its other-app session) |
| **T12b** | Map tab card, chip, I've Arrived, Re-center | 3 to 4 | none |
| **T12c** | Change Drop-off validation and silent failures | 3 to 4 | none |
| **T12d** | Tracker-state combinations | 0 to 2 | check T2 first |
| **T12e** | Photo sheet, camera permission, Delete Account (No only) | 3 | decisions 4 and 5 |
| **T12f** | One more read: View Timeline, Select Organisation | to be sized | none |

Total new: about 12 to 17 tests. Four things stay out: the Stop Info dialog (map markers are not in the accessibility tree), the Mark Arrived dialog (dead code), DISP-006 (cannot end the last ride), and the plain "Trip completed" dialog.

## 6. Questions for the product owner and the developers

1. **Dispatch Log:** the screen exists but nothing opens it. Is it meant to be reachable (where)? If not, it is dead code.
2. **Notifications:** the tab is hidden and the screen shows an "under development" picture. Is it planned for this release?
3. **Possible defects (not tests):** the unused side menu wires "Help" to Change Passcode; `ChangeShuttleFragment` is never opened; the Dispatch Log status bar has no text (`dispatch_status`); `NotificationsFragment` builds its adapter only when the list is empty.
4. Does the server block changing the shuttle during a running trip? (SHUT-008)
5. Texts of the tracker-state dialogs: now read from the source (see 5.1); the live setup for each is still to check.
6. **Silent failures in Change Drop-off:** if the stop list cannot load the app returns to the trip view with no message, and a failed UPDATE shows nothing. Intended?
7. **Dead code to clean up:** the Mark Arrived dialog, the map's "shuttle location" banner, `GuestInfoAdapter`, `FragmentSearchRides`, `ChangeStopFragment`, `DispatchLogFragment`, the hidden side menu and about 20 unused strings (for example `tap_here_to_mark_pickup`, `manually_mark_arrived_at_a_stop`, `switch_to_map_view`, `view_all_stops`, `contact_us`, `video_tutorial`, `under_development`).
8. **Photo size check:** "File size should be less than 1 Mb" only triggers at 2 MB or more (integer division), though the compressor already shrinks the picture to 200x200.
9. **Camera permission denied** on Edit Profile does nothing and shows no message.

## 7. Decisions needed before Phase T9

1. May the tests **rename a staging shuttle and rename it back** (and select another shuttle and go back)? If not, SHUT-003 and SHUT-010 to SHUT-014 are reduced to opening the dialog and cancelling.
2. Which shuttle may be used as the "other" shuttle? Suggestion: "Automated Shuttle-2" (Shuttle-2 has 10 seats and no ADA).
3. Skip HELP-008 (video playback) and the walkthrough pictures if the network is the only thing under test? Suggestion: yes, assert only presence and controls. *(answered: yes, skipped)*

Decisions for T12 (open):
4. **Delete Account (PROF-003):** may the test open the sheet and the "Delete Account?" alert and press **No**? It never presses Yes. Suggestion: yes.
5. **Photo (PROF-001, 002, 004):** open the sheet and revoke/restore the camera permission only? Or also upload a picture through the gallery (fragile, slow)? Suggestion: first only; the upload later if wanted.
6. **T12a** uses Driver 2 as operator again, which signs out its session on your other device for the run. Run it when Driver 2 is not in use?

## 8. Order and size

| Phase | Area | Tests (est.) | Needs |
|---|---|---|---|
| **T9 (done)** | Home header, Select Shuttle, Rename Shuttle | 7 tests (11 scenarios) | done |
| **T10 (done)** | Help Center, Support, tours | 6 tests (7 scenarios) | done |
| **T11 (done)** | Negative checks for Notifications and Dispatch Log | 2 tests | product answers pending |
| **T12 (done, d and f open)** | Dispatch sheets, Map tab, Change Drop-off errors, photo and Delete Account | 12 tests (14 scenarios) | tracker combinations (T12d) and View Timeline (T12f) still open |

Total new: about 15 to 19 tests. After each phase the usual rules apply: an exploration run to record the screens, then assertions only on what was observed, cleanup that restores state, one report per phase, and nothing committed until asked.
