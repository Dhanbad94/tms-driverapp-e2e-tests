# Next features to automate (Phases T13 to T17)

Status: **T14 is done except SRCH-002 and SRCH-003 (blocked by staging); T13 and T15 to T17 are planned only.** Written 2026-11-23.
Source: a read of the ASAP-mode code for the areas not yet covered (profile save, passcode, forgot passcode, timeline, search, organisation, passenger sheet, app-disabled, version checks, session edges, tracker-state dialogs), checked against what the 158 existing tests already do. Statements about the source are marked **CONFIRM** where only the live app can settle them. Test ids use the prefixes below.

## 1. What the read settled (no new tests needed)

| Candidate | Finding | Result |
|---|---|---|
| **T12d** shuttle "Alerts" combinations | Already covered by `StartTripTest` (START-004 to 007: "Alerts" with Belongs to another driver, Shuttle too small, Requires ADA). A single "too small" or single "ADA" dialog cannot be isolated on staging: Shuttle-2 is always held by Automation Driver 2 and Shuttle-1 (own, 15 seats, ADA) never triggers one. "Tracker Unplugged" needs a real tracker state. | **Closed**; the only other path, SELECT ANYWAY, takes the shuttle from Driver 2 and is not tested. |
| **T12f** View Timeline | Reachable only when future booking is on. In ASAP the "add to trip" button stages the ride (bar with CANCEL / CONFIRM), which T6 already tests. | **Dropped** (unreachable in ASAP). |
| Select Organisation, Welcome banner | Only for accounts with several organisations; on a single-organisation account the organisation is disabled with no arrow (tested in SHUT-009). The banner needs a socket event that OD mode does not register. | **Closed** (negative check exists). |
| Forgot passcode: reset | Needs the 6-digit code from the SMS, which the suite cannot read (decision: no OTP reading). The OTP screen, the 6-digit rule, the wrong-code error and Resend are already tested. | **Closed**; the reset screen ("Reset login passcode") stays manual. |
| App disabled / blocked | Needs an admin to disable the test driver; the "blocked" string is dead code. | **Out of scope** (you said it is not needed). |
| Force update | Uses Google Play in-app updates only; a sideloaded build gets none. | **Not testable** here. |
| Session edge cases | Another-device login (silent sign-out) is tested (RT-006). `check-session` is never called by the app; a 401 on any call signs out silently. | **Covered**. |
| Quick tour, Animation Tour, Call Support | Tested in T10. The automatic first-run tour is server state (coachmark version). | **Covered** / manual. |
| Notifications, Dispatch Log, Mark Arrived dialog, Stop Info | Hidden, unreachable or dead (T11, T12). | **Closed**. |
| Delete Account, YES path | Destructive. | **Never automated.** |

## 2. What is worth automating next

### T13: account changes with restore (needs your approval, decision 1)

| ID | Type | Scenario | Expected | Evidence | Pri |
|---|---|---|---|---|---|
| ACC-001 | P | Editing the last name and email and pressing SAVE changes the driver's name everywhere | Server toast (text **CONFIRM**), Profile title and the Home greeting show the new name; reopening Edit Profile shows the new email. **The original values are put back in a `finally` block** and the test fails loudly if the restore does not work | CODE | P1 |
| ACC-002 | C | SAVE turns on after any edit, even one that is changed back (it is a "was touched" flag, not a comparison) | SAVE stays on after typing and reverting; whitespace-only edits also turn it on | CODE | P3 |
| ACC-003 | C | Names stop at 20 characters; the email is optional (cleared email saves) | 25 typed letters leave 20; an empty email is accepted | CODE | P2 |
| ACC-004 | N | Offline, SAVE keeps the driver on Edit Profile with SAVE still on and the connectivity toast | "Connectivity Issue..." | CODE | P2 |
| ACC-005 | P | Change Passcode with the **same** passcode goes through the whole success path | "Set New Passcode" then "Confirm New Passcode", button "update"; the "Passcode Updated / Your passcode is successfully updated." alert; OK returns to Profile. Same value in, so nothing to restore (**CONFIRM** that the session survives it) | CODE | P1 |

Risk: the QA driver's name is read by many other tests ("Hi, Automation Driver", "Automation Driver (You)"). Every test that edits it restores it in `finally`, and a failed restore fails the test.

### T14: search from the Requests screen and pull to refresh (QA driver only)

| ID | Type | Scenario | Expected | Evidence | Pri |
|---|---|---|---|---|---|
| SRCH-001 | P | Search finds an assigned ride and a completed ride, not only the queue | The ride appears under its own section header ("Assigned", "Completed") | CODE; T6 covered the queue | P2 |
| SRCH-002 | P | ASSIGN TO SELF from a search result stages the ride, CONFIRM assigns it | Bar with CANCEL / CONFIRM; toast "Assigned Driver Successfully." (**CONFIRM**); ride appears on Home | CODE | P1 |
| SRCH-003 | N | Assigning a party larger than the shuttle from a search result shows the capacity dialog | "Shuttle doesn't have enough capacity..." with an X only | CODE | P2 |
| SRCH-004 | P | The three-dots menu on a search result is the same as on the Requests card | Same entries per card state | CODE | P3 |
| INT-020 | C | Pull to refresh on the Requests tab brings a ride created while the app was open | Ride appears; works now because a ride can be created through the Rider API without signing the app out (`liveRide`) | CODE | P3 |

#### T14 result (live, staging, emulator, 2026-11-23)
3 tests (`RequestsSearchTest`), all passed. SRCH-002 and SRCH-003 are **blocked**: on 2026-11-23 staging answers **500 "Something went wrong" to every assignment to the QA driver** (through the API and through the app's own ASSIGN TO SELF; reproduced outside the test code with different stops and party sizes), and the Assign Driver screen no longer loads. They are not written; they wait for a fix.

| Scenarios | Result |
|---|---|
| **SRCH-001** | Done. A waiting ride is listed under "Request Queue" with "Requested Just now", its party size, note and both assign buttons. A ride the rider cancelled is listed under "Completed" as "Canceled By Guest (at hh:mm AM/PM)" with **no** assign buttons. Searching the shared start of both names lists both, the queue section first. |
| **SRCH-004** | Done. The three-dots menu on a **waiting** ride is Reject Request, Change Pick-up, Change Drop-off, Mark as Completed and the rider's number (different from an assigned ride's menu); it is identical on the Requests card and on the search result. |
| **INT-020** | Done, with a finding: after the network returns, the app **lists a ride created while it was offline on its own**, even before pulling to refresh; pulling to refresh keeps it. |

What the search does (new): it matches the **start** of a rider name and its **end** digits, but not digits from the middle of a name. The results cover the last 24 hours across all tabs, so test rides from earlier runs show up too ("Canceled By Driver"). On the emulator, swiping on the results while the keyboard is open is read as swipe typing and fills the box with letters (" ft"); the tests hide the keyboard first.

### T15: the rider sheet and pick-up details (QA driver only)

| ID | Type | Scenario | Expected | Evidence | Pri |
|---|---|---|---|---|---|
| PSNG-001 | C | Room and flight appear in the rider sheet when it is opened from Home and from the Arrived screen, not only from the trip view | Settles whether the known problem (PAX-001 sheet, skipped) is **specific to the trip view** or in the data. The source has no entry-point rule; the data likely lacks the fields (**CONFIRM**) | CODE | P1 |
| PSNG-002 | P | After PICK UP is confirmed from the trip view, the Arrived screen already has that rider picked up | Seats show the party; "Picked" with a time appears in the rider sheet | CODE | P1 |
| PSNG-003 | P | The sheet shows "Picked" and "Dropped" with times once those steps are done | Times shown only when set | CODE | P2 |
| PSNG-004 | C | Long and special notes (300 characters, accents, quotes, `<b>tags</b>`, emoji) are shown as text without breaking the card or the sheet | Text shown as typed; layout intact; buttons reachable | CODE | P2 |
| PSNG-005 | C | A rider with a label ("associate") shows the chip | Needs a ride with a label (**CONFIRM** whether the Rider API can set one) | CODE | P3 |

### T16: more than one rider (uses Driver 2 as operator, extended group)

| ID | Type | Scenario | Expected | Evidence | Pri |
|---|---|---|---|---|---|
| MULTI-001 | C | Two riders at one stop: one is picked up, the other is a no-show | Seats count only the picked-up rider; GO TO NEXT STOP is on once both are decided; the no-show's drop-off stop is skipped | CODE | P1 |
| MULTI-002 | C | Three rides on one trip (same pickup) | Route and seat counter add up; seat limit still refuses a third ride that overflows | CODE | P2 |
| MULTI-003 | C | Drop-offs at an intermediate stop with several riders | The intermediate stop lists only its riders; GO TO NEXT STOP is on at once | CODE | P2 |

### T17: non-functional and environment (needs your input, decision 3)

| ID | Scenario | Notes |
|---|---|---|
| PERF-001 | Time budgets: login to Home, START TRIP to the trip view, I've Arrived to the Arrived screen | Recorded already in some tests; needs agreed limits (suggest 10, 10 and 5 seconds on the emulator) |
| ENV-001 | Run the smoke suite on a **real device** and on a **release build** | Needs hardware and a release APK from you; position frequency (GPS-002) needs a backend log |
| ENV-002 | Soak: 10 consecutive trips without restarting the app | Optional; finds leaks and stuck screens |

## 3. Size and order

| Phase | Area | Tests (est.) | Needs |
|---|---|---|---|
| **T13** | Edit Profile save with restore; same-passcode change | 4 to 5 | decision 1 |
| **T14 (done, 2 blocked)** | Search from Requests; pull to refresh | 3 tests | staging assignment (SRCH-002, 003) |
| **T15** | Rider sheet fields; pick-up carried to Arrived; special notes | 4 to 5 | none |
| **T16** | Several riders (no-show mix, three rides) | 3 | Driver 2 (accepted) |
| **T17** | Timing, real device, release build, soak | 1 to 3 | decisions 3 and 4 |

Total new: about 17 to 21 tests. Suggested order: **T14, T15** first (no account changes, no second driver), then **T13** once approved, then **T16**, then **T17** when hardware or limits are available. New tests get the `smoke` tag only if they follow the smoke rules (no SMS, no second driver, no network/GPS/duty switching, no shared-data changes): T14 and T15 qualify, T13 and T16 do not.

## 4. Decisions needed

1. **T13:** may the tests change the QA driver's last name and email (and put them back), and change the passcode to the **same** value? Suggestion: yes, with the restore checks above.
2. **T15, PSNG-005:** is a label ("associate") on a rider something the Rider API can set? If not, drop it.
3. **T17:** which time limits count as a pass (suggestion: 10 s, 10 s and 5 s)?
4. **T17:** can you provide a real device and a release APK, or a backend/socket log for the position frequency?

## 5. Questions kept for later (also in memory)

- Is the trip-view rider sheet's missing room and flight a data problem (the server's `dispatch-status` reply) or a display problem? PSNG-001 will tell which screens are affected.
- The source says the edit-profile success toast, the resend toast and the session messages come from the server; their exact text is not in the app (recorded when first seen).
- Whether `update-passcode` signs other sessions out (not visible in the source).
