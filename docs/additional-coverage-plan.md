# Additional coverage plan: what the ASAP-mode source still offers (Phases T9 to T12)

Status: **Phase T9 is done and passing (2026-10-28); T10 to T12 are planned only.** Decisions taken: renaming and restoring shuttles is allowed, Shuttle-2 is the "other" shuttle, and video playback is skipped.
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

The first-run tour (coachmark) is **server-state**: it shows once per app version until the server stores it. It is a corner scenario and is **not automated**; Launch Quick Tour reaches the same overlay on demand.

Estimated tests: 6 to 7.

## 4. Phase T11: Notifications and Dispatch Log (negative checks only)

| ID | Scenario | Expected | Notes |
|---|---|---|---|
| NOTIF-001 | The bottom bar offers only Home, Profile and Help | No Notifications item is visible | Probably already covered by the shell test; add an assertion if not |
| NOTIF-002 | The Home ride-request icon opens Ride Requests, not Notifications | Requests screen | Already covered in T5/T6 |
| LOG-001 | No control on Home, Profile or Help leads to a Dispatch Log | Not testable as a positive scenario | Waits for the product answer in section 6 |

Estimated tests: 0 to 1. If the product owner confirms that the Dispatch Log or Notifications should be reachable, they become a new phase (the source describes their lists, search with at least 2 characters, the confirm bar, banners and empty texts: "No Dispatches Found within last 24 hours").

## 5. Phase T12: items still to read in the source (not yet planned)

The first read did not cover these ASAP-mode areas. They get a second read before any scenario is written: the **MAP VIEW** tab (`TrackerMapFragment`), the **stop info** dialog, the **Mark Arrived** bottom dialog (when it appears for manual versus automatic arrival), the **dispatch cancel/complete** dialogs (whether they differ from the Requests-card flows already tested), the **tracker state** dialogs' texts, the passenger **change drop-off** failure message, and the profile **photo** picker (permissions; likely not automatable on an emulator).

## 6. Questions for the product owner and the developers

1. **Dispatch Log:** the screen exists but nothing opens it. Is it meant to be reachable (where)? If not, it is dead code.
2. **Notifications:** the tab is hidden and the screen shows an "under development" picture. Is it planned for this release?
3. **Possible defects (not tests):** the unused side menu wires "Help" to Change Passcode; `ChangeShuttleFragment` is never opened; the Dispatch Log status bar has no text (`dispatch_status`); `NotificationsFragment` builds its adapter only when the list is empty.
4. Does the server block changing the shuttle during a running trip? (SHUT-008)
5. Texts of the tracker-state dialogs, and which setup produces each one. (SHUT-007)

## 7. Decisions needed before Phase T9

1. May the tests **rename a staging shuttle and rename it back** (and select another shuttle and go back)? If not, SHUT-003 and SHUT-010 to SHUT-014 are reduced to opening the dialog and cancelling.
2. Which shuttle may be used as the "other" shuttle? Suggestion: "Automated Shuttle-2" (Shuttle-2 has 10 seats and no ADA).
3. Skip HELP-008 (video playback) and the walkthrough pictures if the network is the only thing under test? Suggestion: yes, assert only presence and controls.

## 8. Order and size

| Phase | Area | Tests (est.) | Needs |
|---|---|---|---|
| **T9 (done)** | Home header, Select Shuttle, Rename Shuttle | 7 tests (11 scenarios) | done |
| **T10** | Help Center, Support, tours | 6 to 7 | none |
| **T11** | Negative checks for Notifications and Dispatch Log | 0 to 1 | product answers |
| **T12** | Second source read, then plan | to be sized | none |

Total new: about 15 to 19 tests. After each phase the usual rules apply: an exploration run to record the screens, then assertions only on what was observed, cleanup that restores state, one report per phase, and nothing committed until asked.
