package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.CommonDialog
import tms.driver.screens.shell.EditProfileScreen
import tms.driver.screens.shell.HomeShell
import tms.driver.screens.shell.ProfileScreen
import java.time.Duration

/**
 * Phase T13 (ACC-001 to ACC-005 of docs/next-features-plan.md): saving the QA driver's profile and changing the passcode to the SAME value.
 * Every test that saves puts the original name and email back in a `finally` block and fails if the restore does not work, because many
 * other tests read "Automation Driver".
 */
@Tag("profile")
@Tag("trip")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver account changes (profile save and passcode), always restored")
class AccountChangesTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun openProfile(): ProfileScreen {
        LoginFlow(creds).loginToHome()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(60)))
        return HomeShell().openProfile()
    }

    @Test
    @DisplayName("[ACC-001] Saving a new last name and an email updates the Profile title and the email, and the original values are put back afterwards")
    fun saveProfile() {
        step("Given the driver is on Edit Profile and the original values are noted")
        val profile = openProfile()
        val originalTitle = profile.nameText()
        var edit = profile.openEdit()
        val first = edit.firstNameText(); val last = edit.lastNameText(); val email = edit.emailText()
        try {
            step("When the driver changes the last name and email and taps SAVE, then a toast confirms and the Profile title shows the new name")
            edit.setLastName("$last QA")
            edit.setEmail("qa.driver.test@example.com")
            assertEquals("Profile updated successfully", edit.saveExpectingToast())
            assertEquals("$first $last QA", profile.waitUntilShown().nameText())

            step("And reopening Edit Profile shows the new last name and the email")
            edit = profile.openEdit()
            assertEquals("$last QA", edit.lastNameText())
            assertEquals("qa.driver.test@example.com", edit.emailText())
        } finally {
            step("Restore: the original last name and email are saved again")
            if (!edit.isShown()) edit = profile.waitUntilShown().openEdit()
            edit.setLastName(last); edit.setEmail(email)
            edit.saveExpectingToast()
        }
        step("Then the Profile title and the email are the originals again")
        assertEquals(originalTitle, profile.waitUntilShown().nameText(), "The restore of the name FAILED, fix the account by hand")
        assertEquals(email, profile.openEdit().emailText(), "The restore of the email FAILED, fix the account by hand")
    }

    @Tag("smoke")
    @Test
    @DisplayName("[ACC-002] SAVE is off until something is touched, and stays on after typing and reverting, or after only a space")
    fun saveFlag() {
        step("Given the driver is on Edit Profile with nothing changed")
        val edit = openProfile().openEdit()
        val last = edit.lastNameText()
        assertFalse(edit.isSaveEnabled(), "SAVE should be off before any edit")

        step("When the driver adds a space to the last name, then SAVE turns on")
        edit.setLastName("$last ")
        assertTrue(edit.isSaveEnabled(), "SAVE should turn on after an edit")

        step("And typing the original value back leaves SAVE on (it is a 'was touched' flag)")
        edit.setLastName(last)
        assertTrue(edit.isSaveEnabled(), "SAVE stays on although the value is the original")
        edit.leave()
    }

    @Tag("smoke")
    @Test
    @DisplayName("[ACC-003] The first and last names stop at 20 characters")
    fun nameLimit() {
        step("Given the driver is on Edit Profile")
        val edit = openProfile().openEdit()
        val first = edit.firstNameText(); val last = edit.lastNameText()

        step("When 25 letters are typed into each name, then only 20 stay")
        edit.setFirstName("A".repeat(25)); edit.setLastName("B".repeat(25))
        assertEquals(20, edit.firstNameText().length)
        assertEquals(20, edit.lastNameText().length)

        step("And leaving without saving keeps the originals")
        edit.setFirstName(first); edit.setLastName(last)
        edit.leave()
        assertEquals("$first $last", ProfileScreen().waitUntilShown().nameText())
    }

    @Test
    @Tag("network")
    @DisplayName("[ACC-004] Saving while offline keeps the driver on Edit Profile with a connectivity message, and nothing is saved")
    fun saveOffline() {
        val adb = Adb()
        step("Given the driver is on Edit Profile and has edited the last name")
        val profile = openProfile()
        val edit = profile.openEdit()
        val last = edit.lastNameText()
        try {
            edit.setLastName("$last QA")
            assertTrue(adb.setOffline(true), "Could not switch the device offline")
            Thread.sleep(3_000)

            step("When the driver taps SAVE offline, then a connectivity message is shown and Edit Profile stays open")
            val toast = edit.saveExpectingToast()
            observe("offline-save-message", toast ?: "(none)")
            assertTrue(toast?.contains("Connectivity", ignoreCase = true) == true, "Expected a connectivity message but got: $toast")
            assertTrue(edit.isShown(), "Edit Profile should stay open")
        } finally {
            adb.setOffline(false)
            Thread.sleep(3_000)
        }
        step("And after the network is back, leaving without saving keeps the original name")
        edit.leave()
        assertFalse(profile.waitUntilShown().nameText().endsWith("QA"), "Nothing should have been saved")
    }

    @Test
    @DisplayName("[ACC-005] Changing the passcode to the same value goes through both steps and ends with 'Passcode Updated'; the driver stays signed in")
    fun changePasscodeSameValue() {
        step("Given the driver is on Change Passcode and enters the current passcode")
        val profile = openProfile()
        val change = profile.openChangePasscode()
        assertEquals("Enter Current Passcode", change.labelText())
        change.enterPrimary(creds.passcode)
        change.submit()
        assertEquals("Set New Passcode", change.labelText())

        step("When the driver sets the SAME passcode and confirms it, then the alert says it was updated")
        change.enterPrimary(creds.passcode)
        change.enterConfirm(creds.passcode)
        assertTrue(change.isSubmitEnabled(), "The button should be on once both fields hold four digits")
        change.submit()
        val dialog = CommonDialog().waitUntilShown()
        assertEquals("Passcode Updated", dialog.titleText())
        assertEquals("Your passcode is successfully updated.", dialog.messageText())

        step("And OK returns to Profile with the driver still signed in")
        dialog.confirm()
        assertEquals("Automation Driver", profile.waitUntilShown().nameText())
    }

    private fun observe(name: String, value: String) {
        Allure.addAttachment(name, value)
        LoggerFactory.getLogger("observed").info("OBSERVED {} = {}", name, value)
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
