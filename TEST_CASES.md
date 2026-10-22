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

**[API-001..014] The Driver API client logs in, finds, assigns, starts and cleans up rides** *(runs against a local stub; no device)*
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

**[TRIP-001] A ride assigned to the driver through the API appears on the driver's home screen** *(not yet executed)*
- *Given* the QA driver is logged in and the home screen shows no trips, and a separate dispatcher account is logged in to the Driver API and finds the waiting ride
- *When* the dispatcher assigns the ride to the QA driver
- *Then* the app shows the trip without the "no trips" message, and the ride is cancelled afterwards so nothing stays active.
- Runs only when the Driver API settings are provided; the home-screen ids used are unverified.

## Not automated (decisions)

- Driver-app-disabled dialog (AUTH-009): no disabled account will be provided.
- First login, create passcode and reset passcode (PASS-004, PASS-005, AUTH-008): corner scenarios.
- Forced logouts by the server, another device or an expired token (SES-005 to SES-007).
