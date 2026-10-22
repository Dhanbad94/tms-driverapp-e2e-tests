package tms.driver.auth

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.wait.Toasts
import tms.driver.flows.LaunchFlow
import tms.driver.flows.LoginFlow
import tms.driver.screens.SystemDialogs
import tms.driver.screens.auth.LoginScreen
import tms.driver.screens.shell.CommonDialog
import tms.driver.screens.shell.ConnectivityBanner
import tms.driver.screens.shell.HomeShell
import tms.driver.screens.shell.HomeShell.Tab
import java.time.Duration
import kotlin.random.Random

/**
 * Driver app (on-demand / ASAP): login, passcode, session, home screen, profile and OTP behaviour.
 *
 * Every test has a plain-English title (@DisplayName) and Given / When / Then steps that appear in the
 * report. The scenario ID in square brackets links each test to automation-planning/05-test-scenarios.md.
 * A readable summary of every test is in TEST_CASES.md.
 *
 * Credentials come from TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE; tests that need them are skipped when they
 * are not set. Each test starts from a cleared app, so it always begins on the Login screen.
 *
 * Tags: "sms" sends a real SMS to the QA phone, "network" switches the device offline, "duty" changes
 * (and restores) the driver's duty state.
 */
@Tag("auth")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver app (on-demand): login, session, home and profile")
class OdAsapDriverApp {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    // ================================================================ Login screen

    @Test
    @DisplayName("[AUTH-003] Tapping the phone number area opens the login form")
    fun `AUTH-003 tapping the number area expands the form`() {
        step("Given the app has just been opened and the Login screen is showing")
        val login = LaunchFlow().launchToLogin()
        assertFalse(login.isPhoneFieldEnabled(), "The phone number field should be disabled before the form is opened")
        assertFalse(login.isTermsShown(), "The terms and privacy text should be hidden before the form is opened")

        step("When the driver taps the phone number area")
        login.expandForm()

        step("Then the phone field becomes usable, the terms text appears and Submit stays disabled")
        assertTrue(login.isPhoneFieldEnabled(), "The phone number field should be enabled after tapping the area")
        assertTrue(login.isTermsShown(), "The terms and privacy text should be visible once the form is open")
        assertFalse(login.isSubmitEnabled(), "Submit should be disabled while the phone number is empty")
    }

    @Test
    @DisplayName("[AUTH-004] Submit is enabled only when a full phone number has been typed")
    fun `AUTH-004 submit enablement follows the phone number length`() {
        step("Given the login form is open and the phone number is empty")
        val login = LaunchFlow().launchToLogin().expandForm()
        assertFalse(login.isSubmitEnabled(), "Submit should be disabled for an empty phone number")

        step("When the driver types 1 digit, then 2 digits")
        login.enterPhone("9")
        assertFalse(login.isSubmitEnabled(), "Submit should be disabled for 1 digit")
        login.clearPhone().enterPhone("98")
        assertFalse(login.isSubmitEnabled(), "Submit should be disabled for 2 digits")

        step("And when the driver types a full 10-digit number")
        login.clearPhone().enterPhone("9876543210")
        assertTrue(login.isSubmitEnabled(), "Submit should be enabled for a 10-digit number")

        step("Then clearing the field disables Submit again")
        login.clearPhone()
        assertFalse(login.isSubmitEnabled(), "Submit should be disabled again once the number is cleared")
    }

    @Test
    @DisplayName("[AUTH-005] The phone number field stops accepting digits after 15")
    fun `AUTH-005 phone field accepts at most 15 characters`() {
        step("Given the login form is open")
        val login = LaunchFlow().launchToLogin().expandForm()

        step("When the driver types 20 digits")
        login.enterPhone("98765432109876543210")

        step("Then only the first 15 digits are kept")
        assertEquals(15, login.phoneDigitCount(), "The phone number field should hold at most 15 digits")
    }

    @Test
    @Tag("smoke")
    @DisplayName("[AUTH-006] An unregistered phone number is rejected with a clear message")
    fun `AUTH-006 unregistered phone number shows the inline error`() {
        // A random 10-digit number is used each run; no SMS is sent for an unknown number.
        val randomNumber = (6..9).random().toString() + (1..9).joinToString("") { Random.nextInt(0, 10).toString() }

        step("Given the login form is open with the driver's country selected")
        val login = LaunchFlow().launchToLogin().expandForm()
        login.selectCountry(creds.countryName, creds.isdCode)

        step("When the driver enters a random phone number that is not registered and taps Submit")
        login.enterPhone(randomNumber)
        assertTrue(login.isSubmitEnabled(), "Submit should be enabled for a 10-digit number")
        login.submit()

        step("Then the message 'Phone not registered...' is shown and Submit is hidden")
        assertEquals(
            "Phone not registered. Please contact Manager to get your number registered.",
            login.errorText(),
        )
        assertFalse(login.isSubmitEnabled(), "Submit should be hidden while the error message is showing")

        step("And editing the phone number clears the message")
        login.clearPhone().enterPhone("9")
        assertFalse(login.isErrorShown(), "The error message should disappear when the number is edited")
    }

    @Test
    @DisplayName("[AUTH-010] Choosing a country in the country picker updates the dial code")
    fun `AUTH-010 country picker selection is applied`() {
        step("Given the login form is open")
        val login = LaunchFlow().launchToLogin().expandForm()

        step("When the driver opens the country picker and chooses the driver's country")
        login.selectCountry(creds.countryName, creds.isdCode)

        step("Then the dial code shown next to the phone number matches that country")
        assertEquals(creds.isdCode.filter { it.isDigit() }, login.countryCodeText().filter { it.isDigit() })
    }

    @Test
    @Tag("network")
    @DisplayName("[AUTH-012] Submitting the phone number while offline shows a connectivity message")
    fun `AUTH-012 submitting while offline shows the connectivity toast`() {
        // Confirmed on staging: the Login screen shows no connectivity banner, only the message below.
        val adb = Adb()
        step("Given the login form is filled in with the driver's phone number")
        val login = LaunchFlow().launchToLogin().expandForm()
        login.selectCountry(creds.countryName, creds.isdCode)
        login.enterPhone(creds.phoneDigits())
        try {
            step("When the device goes offline and the driver taps Submit")
            assertTrue(adb.setOffline(true), "Could not switch the device offline")
            Toasts.mark(DriverSession.driver)
            login.submit()

            step("Then a 'Connectivity Issue' message is shown")
            val message = Toasts.read(DriverSession.driver, Duration.ofSeconds(20))
            assertNotNull(message, "A message should have been shown after submitting while offline")
            assertTrue(message!!.contains("Connectivity Issue"), "Unexpected message: $message")
        } finally {
            restoreNetwork(adb)
        }
    }

    // ================================================================ Passcode screen

    @Test
    @Tag("smoke")
    @DisplayName("[PASS-001] A driver with the correct passcode reaches the home screen")
    fun `PASS-001 valid passcode lands on the home shell`() {
        step("Given the driver is on the Login screen")
        step("When the driver enters their phone number and the correct 4-digit passcode")
        val home = LoginFlow(creds).loginToHome()

        step("Then the home screen is shown with its toolbar and bottom navigation")
        assertTrue(home.isShown(), "The home screen should be visible after logging in")
        assertFalse(home.isOrgPickerShown(), "A single-organisation driver should not be asked to pick an organisation")
    }

    @Test
    @Tag("smoke")
    @DisplayName("[PASS-002] A wrong passcode is rejected with a clear message")
    fun `PASS-002 wrong passcode shows inline error and stays on passcode screen`() {
        step("Given the driver has entered their phone number and is on the passcode screen")
        val passcode = LoginFlow(creds).toPasscodeScreen()

        step("When the driver enters a 4-digit passcode that is wrong and taps Submit")
        passcode.enterPasscode(creds.wrongPasscode()).submit()

        step("Then the message 'Invalid Passcode. Please try again.' is shown and the driver stays on this screen")
        assertEquals("Invalid Passcode. Please try again.", passcode.errorText())
        assertTrue(passcode.isShown(), "The driver should remain on the passcode screen")
    }

    @Test
    @DisplayName("[PASS-003] Submit is available only once exactly four passcode digits are entered")
    fun `PASS-003 submit is enabled only at exactly four digits`() {
        step("Given the driver is on the passcode screen")
        val passcode = LoginFlow(creds).toPasscodeScreen()
        assertEquals("Enter passcode to login", passcode.titleText())
        assertTrue(passcode.isForgotShown(), "The 'Forgot passcode' link should be shown on the login passcode screen")
        assertTrue(passcode.isSubmitEnabled() != true, "Submit should not be enabled with no digits")

        step("When the driver enters 1 digit, then 3 digits")
        val digits = creds.passcode
        passcode.enterPasscode(digits.take(1))
        assertTrue(passcode.isSubmitEnabled() != true, "Submit should not be enabled with 1 digit")
        passcode.enterPasscode(digits.substring(1, 3))
        assertTrue(passcode.isSubmitEnabled() != true, "Submit should not be enabled with 3 digits")

        step("And when the fourth digit is entered")
        passcode.enterPasscode(digits.drop(3))
        assertEquals(true, passcode.isSubmitEnabled(), "Submit should be enabled with exactly 4 digits")

        step("Then a fifth digit is ignored and Submit stays enabled")
        passcode.enterPasscode("5")
        assertEquals(true, passcode.isSubmitEnabled(), "A fifth digit should not change the Submit state")
    }

    @Test
    @DisplayName("[PASS-006] The passcode is hidden while it is being typed")
    fun `PASS-006 passcode is masked in the field`() {
        step("Given the driver is on the passcode screen")
        val passcode = LoginFlow(creds).toPasscodeScreen()

        step("When the driver types the passcode")
        passcode.enterPasscode(creds.passcode)

        step("Then the digits are not readable as plain text on screen")
        assertFalse(
            passcode.fieldText().contains(creds.passcode),
            "The passcode must not be exposed as plain text",
        )
    }

    // ================================================================ Session

    @Test
    @Tag("smoke")
    @DisplayName("[SES-001] A logged-in driver stays logged in after the app is closed and reopened")
    fun `SES-001 session survives app restart`() {
        step("Given the driver is logged in and on the home screen")
        LoginFlow(creds).loginToHome()
        val appPackage = DriverSession.config.appPackage

        step("When the app is closed completely and opened again")
        DriverSession.driver.terminateApp(appPackage)
        DriverSession.driver.activateApp(appPackage)

        step("Then the driver goes straight to the home screen without logging in again")
        assertTrue(HomeShell().waitUntilShown().isShown(), "The home screen should open without a new login")
    }

    @Test
    @Tag("smoke")
    @DisplayName("[SES-002 / SES-004] Signing out from Profile asks for confirmation and fully ends the session")
    fun `SES-002 and SES-004 sign out asks for confirmation, returns to Login and clears the session`() {
        step("Given the driver is logged in")
        val flow = LoginFlow(creds)
        flow.loginToHome()

        step("When the driver opens Profile and taps Sign Out")
        val dialog = HomeShell().openProfile().tapSignOut()

        step("Then a 'Sign Out?' confirmation asks 'Are you sure you want to sign out?'")
        assertEquals("Sign Out?", dialog.titleText())
        assertEquals("Are you sure you want to sign out?", dialog.messageText())

        step("And choosing Cancel keeps the driver logged in")
        dialog.cancel()
        assertTrue(HomeShell().isShown(), "Cancelling should leave the driver logged in")

        step("When the driver signs out and confirms")
        val login: LoginScreen = flow.logout()

        step("Then the Login screen is shown, the country is remembered and the phone number is empty")
        assertTrue(login.isShown(), "The Login screen should be shown after signing out")
        login.expandForm()
        assertEquals(
            creds.isdCode.filter { it.isDigit() }, login.countryCodeText().filter { it.isDigit() },
            "The country code should be remembered after signing out",
        )
        assertEquals(0, login.phoneDigitCount(), "The phone number should not be pre-filled after signing out")

        step("And reopening the app does not bring the session back")
        val appPackage = DriverSession.config.appPackage
        DriverSession.driver.terminateApp(appPackage)
        DriverSession.driver.activateApp(appPackage)
        assertTrue(LoginScreen().waitUntilShown().isShown(), "Reopening the app should show the Login screen")
    }

    @Test
    @Tag("duty")
    @DisplayName("[SES-003 / SH-003 / SH-004] Switching duty on and off shows or hides the toolbar Sign Out button")
    fun `SES-003 and SH-003 SH-004 duty toggle controls Sign Out visibility and is restored`() {
        step("Given the driver is logged in and the home screen shows their current duty state")
        LoginFlow(creds).loginToHome()
        val home = HomeShell()
        val initial = home.dutyState()
        val other = if (initial == "ON") "OFF" else "ON"
        assertEquals(initial == "OFF", home.isHomeSignOutVisible(), "Sign Out should be visible only when off duty (duty is $initial)")

        step("When the driver switches duty to $other")
        home.toggleDuty()
        try {
            step("Then duty shows $other and the toolbar Sign Out button follows it")
            assertTrue(home.waitForDuty(other), "Duty should change from $initial to $other")
            assertEquals(other == "OFF", home.isHomeSignOutVisible(), "Sign Out should be visible only when off duty (duty is $other)")
        } finally {
            if (home.dutyState() != initial) {
                home.toggleDuty()
                home.waitForDuty(initial)
            }
        }
        step("And the driver's original duty state is restored")
        assertEquals(initial, home.dutyState(), "The original duty state should be restored")
    }

    @Test
    @Tag("duty")
    @DisplayName("[SES-003b] The toolbar Sign Out button logs the driver out immediately, with no confirmation")
    fun `SES-003b toolbar Sign Out logs out immediately without a confirmation dialog`() {
        step("Given the driver is logged in and off duty (so the toolbar Sign Out button is visible)")
        val flow = LoginFlow(creds)
        flow.loginToHome()
        val home = HomeShell()
        val wasOn = home.dutyState() == "ON"
        if (wasOn) {
            home.toggleDuty()
            assertTrue(home.waitForDuty("OFF"), "Duty should switch to OFF")
        }
        assertTrue(home.isHomeSignOutVisible(), "The toolbar Sign Out button should be visible while off duty")

        step("When the driver taps the toolbar Sign Out button")
        home.tapHomeSignOut()

        step("Then no confirmation appears and the Login screen is shown")
        assertFalse(CommonDialog().isShown(), "No confirmation dialog is expected on this path")
        assertTrue(LoginScreen().waitUntilShown().isShown(), "The Login screen should be shown after signing out")

        if (wasOn) {
            step("And the driver's original duty state (ON) is restored for the next test")
            val again = LoginFlow(creds).loginToHome()
            if (again.dutyState() != "ON") {
                again.toggleDuty()
                assertTrue(again.waitForDuty("ON"), "Duty should be restored to ON")
            }
        }
    }

    // ================================================================ Home screen

    @Test
    @DisplayName("[SH-001 / SH-002] The home screen greets the driver and the bottom tabs open their screens")
    fun `SH-001 and SH-002 home shell shows the driver and the tabs switch screens`() {
        step("Given the driver has just logged in")
        LoginFlow(creds).loginToHome()
        val home = HomeShell()

        step("Then the toolbar shows the driver's name and the Home, Profile and Help tabs are available")
        assertTrue(home.driverNameText().isNotBlank(), "The driver's name should be shown in the toolbar")
        // The staging OD/ASAP build shows Home, Profile and Help (there is no Notifications tab).
        val present = home.presentTabs()
        Allure.addAttachment("tabs-present", "text/plain", present.toString())
        assertTrue(present.containsAll(listOf(Tab.HOME, Tab.PROFILE, Tab.HELP)), "Expected Home, Profile and Help tabs, but found $present")
        assertTrue(home.isSelected(Tab.HOME), "Home should be the selected tab after logging in")

        step("When the driver taps Profile, then Help, then Home")
        var anrCount = 0
        fun switchTo(tab: Tab) {
            home.open(tab)
            // On the software-rendered emulator an "isn't responding" dialog was once seen after leaving Help.
            if (SystemDialogs(DriverSession.driver).waitOutAnrIfPresent()) anrCount++
            assertTrue(home.isSelected(tab), "The $tab tab should be selected")
        }
        switchTo(Tab.PROFILE)
        switchTo(Tab.HELP)
        switchTo(Tab.HOME)

        step("Then each tab opens, and the app never shows an 'isn't responding' dialog")
        Allure.addAttachment("anr-dialogs-observed", "text/plain", anrCount.toString())
        assertEquals(0, anrCount, "An 'isn't responding' dialog appeared while switching tabs (see attachment)")
    }

    @Test
    @Tag("network")
    @DisplayName("[SH-009] Losing the network shows an offline banner and the app recovers when it returns")
    fun `SH-009 offline banner appears and a back-online banner follows`() {
        val adb = Adb()
        step("Given the driver is logged in and on the home screen")
        LoginFlow(creds).loginToHome()
        val banner = ConnectivityBanner()
        try {
            step("When the device loses its network connection")
            assertTrue(adb.setOffline(true), "Could not switch the device offline")

            step("Then an offline banner is shown")
            val offline = banner.waitUntilShown()
            assertTrue(offline.isNotBlank(), "The offline banner should show some text")

            step("When the network comes back")
            assertTrue(adb.setOffline(false), "Could not restore the network")
            // The green "Back online" banner lasts about 2 seconds; record whether it was seen.
            val online = banner.waitForBackOnline(Duration.ofSeconds(30))
            Allure.addAttachment("back-online-banner-text", "text/plain", online ?: "(not observed)")

            step("Then the offline banner disappears")
            assertTrue(banner.waitUntilHidden(Duration.ofSeconds(30)), "The offline banner should disappear after reconnecting")
        } finally {
            restoreNetwork(adb)
        }
    }

    // ================================================================ Profile and passcode change

    @Test
    @DisplayName("[PR-001 / PR-002] Profile shows the driver's details and Edit Profile rejects missing or invalid fields")
    fun `PR-001 and PR-002 profile shows details and edit-profile validates required fields`() {
        step("Given the driver is logged in and opens the Profile tab")
        LoginFlow(creds).loginToHome()
        val profile = HomeShell().openProfile()

        step("Then the driver's name and the app version are shown")
        assertTrue(profile.nameText().isNotBlank(), "The driver's name should be shown")
        assertTrue(
            Regex("v?\\d+\\.\\d+\\.\\d+.*").matches(profile.versionText().trim()),
            "The version should look like v1.2.3 but was '${profile.versionText()}'",
        )

        step("When the driver opens Edit Profile")
        val edit = profile.openEdit()
        val first = edit.firstNameText()
        val last = edit.lastNameText()

        step("And clears the first name and taps Save, then the message 'Please enter your first name' is shown")
        edit.setFirstName("")
        assertEquals("Please enter your first name", edit.saveExpectingToast())
        edit.setFirstName(first)

        step("And clears the last name and taps Save, then the message 'Please enter your last name' is shown")
        edit.setLastName("")
        assertEquals("Please enter your last name", edit.saveExpectingToast())
        edit.setLastName(last)

        step("And types an invalid email and taps Save, then the message 'Please enter valid email' is shown")
        edit.setEmail("not-an-email")
        assertEquals("Please enter valid email", edit.saveExpectingToast())

        step("And leaving without saving changes nothing")
        edit.leave()
    }

    @Test
    @DisplayName("[CP-001 / CP-002] Change Passcode rejects a wrong current passcode and a new passcode that does not match")
    fun `CP-001 and CP-002 change passcode rejects a wrong current passcode and a mismatched confirmation`() {
        step("Given the driver opens Profile and then Change Passcode")
        LoginFlow(creds).loginToHome()
        val change = HomeShell().openProfile().openChangePasscode()
        assertEquals("Enter Current Passcode", change.labelText())

        step("When the driver enters a wrong current passcode, then the message 'Invalid passcode. Please retry.' is shown")
        change.enterPrimary(creds.wrongPasscode())
        change.submit()
        assertEquals("Invalid passcode. Please retry.", change.errorText())

        step("And when the driver enters the correct current passcode, they are asked to set a new one")
        change.enterPrimary(creds.passcode)
        change.submit()
        assertEquals("Set New Passcode", change.labelText())

        step("And when the new passcode and its confirmation differ, then 'Passcodes do not match. Please retry.' is shown")
        change.enterPrimary(creds.otherPasscode(2))
        change.enterConfirm(creds.otherPasscode(3))
        change.submit()
        assertEquals("Passcodes do not match. Please retry.", change.errorText())
    }

    // ================================================================ OTP (sends real SMS)

    @Test
    @Tag("sms")
    @DisplayName("[OTP-001 / OTP-002 / OTP-004 / OTP-005] The OTP screen shows the right titles, needs six digits, rejects a wrong code and can resend")
    fun `OTP-001 OTP-002 OTP-004 OTP-005 forgot-passcode OTP screen titles, boundary, wrong code and resend`() {
        // NOTE: "Forgot passcode" and "Resend" send REAL SMS messages to the QA phone. The code is never used.
        step("Given the driver is on the passcode screen and taps 'Forgot passcode'")
        val otp = LoginFlow(creds).toPasscodeScreen().tapForgotPasscode()
        assertTrue(otp.isShown(), "The OTP screen should be shown after tapping 'Forgot passcode'")

        step("Then the screen is titled 'Forgot passcode?' and asks for the 6-digit code sent to the phone")
        assertEquals("Forgot passcode?", otp.titleText())
        assertEquals("To reset passcode, enter 6-digit code sent to your phone.", otp.subtitleText())

        step("When the driver types 5 digits, Next stays disabled; with 6 digits, Next is enabled")
        assertFalse(otp.isNextEnabled(), "Next should be disabled when the field is empty")
        otp.enterCode("95831")
        assertFalse(otp.isNextEnabled(), "Next should be disabled with 5 digits")
        otp.enterCode("958314")  // the field text is replaced, so the full 6-digit value is sent
        assertTrue(otp.isNextEnabled(), "Next should be enabled with 6 digits")

        step("And when a wrong 6-digit code is submitted, an error is shown and the field is cleared")
        otp.submit()
        assertTrue(otp.isShown(), "A wrong code should keep the driver on the OTP screen")
        assertTrue(otp.isErrorShown(), "An error message should be shown for a wrong code")
        val message = otp.errorText()
        assertTrue(message.isNotBlank(), "The error message should not be blank")
        Allure.addAttachment("wrong-otp-error-text", "text/plain", message)
        assertEquals("", otp.codeFieldText().filter { it.isDigit() }, "The code field should be cleared after a wrong code")

        step("And tapping 'Resend Code' keeps the driver on the OTP screen with an empty field")
        otp.tapResend()
        assertTrue(otp.isShown(), "The driver should stay on the OTP screen after Resend")
        assertEquals("", otp.codeFieldText().filter { it.isDigit() }, "The code field should be empty after Resend")

        step("Then going Back leaves the OTP screen")
        otp.tapBack()
        assertFalse(otp.isShown(Duration.ofSeconds(3)), "Back should leave the OTP screen")
    }

    // ================================================================ helpers

    /** Records a plain-English step in the report and the log. */
    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }

    /** Always leave the device online, whatever the test did. */
    private fun restoreNetwork(adb: Adb) {
        repeat(5) {
            if (adb.setOffline(false)) return
            Thread.sleep(1000)
        }
    }
}
