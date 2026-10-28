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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.wait.Toasts
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shuttle.RenameShuttleDialog
import tms.driver.screens.shuttle.SelectShuttleScreen
import tms.driver.screens.shuttle.ShuttleOfAnotherDriverDialog
import java.time.Duration

/**
 * Phase T9: the shuttle on Home, the Select Shuttle list and Rename Shuttle (SHUT-001 to SHUT-015 of docs/additional-coverage-plan.md).
 * Staging has two shuttles. Shuttle-1 (15 seats, ADA) is the QA driver's own; Shuttle-2 (10 seats, no ADA) is held by Automation
 * Driver 2, so a second shuttle cannot really be taken: "SELECT ANYWAY" would take it from that driver, so it is never tapped.
 * Renaming changes shared data: the tests rename Shuttle-1 and ALWAYS rename it back in a finally block.
 */
@Tag("trip")
@Tag("shuttle")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver shuttle: Home header, Select Shuttle and Rename Shuttle")
class ShuttleTest {
    private lateinit var creds: Credentials
    private val own = "Automated Shuttle-1"
    private val other = "Automated Shuttle-2"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun loginToHome(): OdHomeScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)), "The driver should start with no trip")
        return home
    }

    private fun openList(home: OdHomeScreen): SelectShuttleScreen { home.tapShuttle(); return SelectShuttleScreen().waitUntilShown() }

    @Test
    @DisplayName("[SHUT-001 / SHUT-002 / SHUT-004 / SHUT-009] Home shows the organisation and shuttle; tapping the shuttle lists both shuttles with seats and driver, and CONFIRM is already on")
    fun headerAndList(rides: RideFixture) {
        step("Given the driver is on Home")
        val home = loginToHome()

        step("Then the header shows the organisation and the shuttle")
        assertEquals("Automated OD ASAP QA", home.organisationText())
        assertEquals(own, home.shuttleText())

        step("And the organisation is not a link, as the account has only one")
        assertFalse(home.isOrganisationEnabled(), "With one organisation there is nothing to switch to")

        step("When the driver taps the shuttle, then 'Select Shuttle' lists both shuttles")
        val list = openList(home)
        assertEquals("Select Shuttle", list.titleText())
        val rows = list.rows().associateBy { it.name }
        assertEquals(setOf(own, other), rows.keys)

        step("And each row shows its seats and the driver using it, and the current shuttle has the check mark")
        assertEquals("15", rows.getValue(own).seats)
        assertTrue(rows.getValue(own).selected, "The current shuttle should carry the check mark")
        assertEquals("Automation Driver", rows.getValue(own).driver)
        assertEquals("10", rows.getValue(other).seats)
        assertFalse(rows.getValue(other).selected, "The other shuttle must not be selected")
        assertEquals("Automation Driver 2", rows.getValue(other).driver)

        step("And CONFIRM is on at once because the current shuttle is already selected")
        assertEquals("CONFIRM", list.confirmText())
        assertTrue(list.isConfirmEnabled())
    }

    @Test
    @DisplayName("[SHUT-003] Confirming the current shuttle returns Home with the same shuttle")
    fun confirmCurrentShuttle(rides: RideFixture) {
        step("Given the driver has opened Select Shuttle")
        val home = loginToHome()
        val list = openList(home)

        step("When the driver taps CONFIRM without changing anything, then Home shows the same shuttle")
        list.confirm()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)), "Home should be shown again")
        assertEquals(own, home.shuttleText())
    }

    @Test
    @DisplayName("[SHUT-005 / SHUT-006] Search narrows the shuttles by part of the name in any case, and says 'No match found' otherwise")
    fun searching(rides: RideFixture) {
        step("Given the driver has opened Select Shuttle")
        val list = openList(loginToHome())

        step("When the driver searches for '2', then only Shuttle-2 is left")
        list.openSearch().search("2")
        assertEquals(listOf(other), list.rowNames())

        step("And 'SHUTTLE-1' (capitals) leaves only Shuttle-1")
        list.search("SHUTTLE-1")
        assertEquals(listOf(own), list.rowNames())

        step("And something that matches nothing says 'No match found'")
        list.search("zzqq")
        assertTrue(list.noMatchShown(), "'No match found' should be shown")
        assertTrue(list.rowNames().isEmpty())

        step("And closing the search brings both shuttles back")
        list.closeSearch()
        assertEquals(setOf(own, other), list.rowNames().toSet())
    }

    @Test
    @DisplayName("[SHUT-007] A shuttle held by another driver asks 'Belongs to another driver'; PICK ANOTHER keeps the current shuttle")
    fun shuttleOfAnotherDriver(rides: RideFixture) {
        step("Given Shuttle-2 is used by Automation Driver 2")
        val home = loginToHome()
        val list = openList(home)
        assumeTrue(list.rows().firstOrNull { it.name == other }?.driver == "Automation Driver 2", "Shuttle-2 is not held by Automation Driver 2 right now, so this scenario cannot be set up")

        step("When the driver chooses Shuttle-2, then a dialog says it belongs to another driver and names that driver")
        list.tapRow(other)
        val dialog = ShuttleOfAnotherDriverDialog().waitUntilShown()
        assertEquals("Belongs to another driver", dialog.labelText())
        assertEquals("This shuttle is assigned to:", dialog.messageText())
        assertEquals("Automation Driver 2", dialog.driverName())
        assertEquals("SELECT ANYWAY", dialog.selectAnywayText())
        assertEquals("PICK ANOTHER", dialog.pickAnotherText())

        step("When the driver chooses PICK ANOTHER (SELECT ANYWAY is never tapped, it would take the shuttle from that driver), then the dialog closes and Home keeps Shuttle-1")
        dialog.pickAnother()
        assertFalse(ShuttleOfAnotherDriverDialog().isShown(Duration.ofSeconds(2)))
        list.back()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
        assertEquals(own, home.shuttleText())
    }

    @Test
    @DisplayName("[SHUT-010 / SHUT-011 / SHUT-012 / SHUT-013] Rename Shuttle opens with the current name, refuses a one-letter name, stops at 30 characters and keeps CHANGE off for a blank or unchanged name")
    fun renameRules(rides: RideFixture) {
        step("Given the driver has opened the pencil of Shuttle-1")
        val list = openList(loginToHome())
        val dialog = list.openRename(own)

        step("Then the dialog is titled 'Rename Shuttle', shows the current name, offers CANCEL and CHANGE, and CHANGE is off")
        assertEquals("Rename Shuttle", dialog.titleText())
        assertEquals(own, dialog.nameText())
        assertEquals("CANCEL", dialog.cancelText())
        assertEquals("CHANGE", dialog.changeText())
        assertFalse(dialog.isChangeEnabled(), "Nothing has changed yet, so CHANGE should be off")

        step("When the name is cleared, then CHANGE stays off; and typing the original name back keeps it off")
        dialog.setName("")
        assertFalse(dialog.isChangeEnabled(), "A blank name must not be allowed")
        dialog.setName(own)
        assertFalse(dialog.isChangeEnabled(), "The unchanged name must not be allowed")

        step("When a one-letter name is typed and CHANGE is tapped, then 'Please enter valid name' is shown and nothing is renamed")
        dialog.setName("A")
        if (dialog.isChangeEnabled()) dialog.change()
        assertEquals("Please enter valid name", dialog.errorText()?.trim())

        step("When 40 characters are typed, then only 30 are kept")
        dialog.setName("X".repeat(40))
        assertEquals(30, dialog.nameText().length)

        step("And CANCEL closes the dialog and the list still shows the original name")
        dialog.cancel()
        assertFalse(RenameShuttleDialog().isShown(Duration.ofSeconds(2)))
        assertTrue(own in list.rowNames())
    }

    @Test
    @DisplayName("[SHUT-014] Renaming the shuttle shows a confirmation and the new name; renaming it back restores it")
    fun renameAndRestore(rides: RideFixture) {
        val renamed = "$own R"
        step("Given the driver has opened the pencil of Shuttle-1")
        val home = loginToHome()
        val list = openList(home)
        var nameNow = own
        try {
            step("When the name is changed to '$renamed' and CHANGE is tapped, then the toast confirms it and the list shows the new name")
            val dialog = list.openRename(own)
            dialog.setName(renamed)
            assertTrue(dialog.isChangeEnabled(), "CHANGE should be on for a new name")
            Toasts.mark(DriverSession.driver)
            dialog.change()
            val toast = Toasts.read(DriverSession.driver, Duration.ofSeconds(10))
            observe("rename-toast", toast ?: "(none)")
            assertEquals("Shuttle name changed successfully!!", toast)
            assertTrue(waitForRow(list, renamed), "The list should show '$renamed', but shows ${list.rowNames()}")
            nameNow = renamed
        } finally {
            if (nameNow != own || own !in list.rowNames()) restoreName(list, nameNow)
        }

        step("Then the original name is back in the list and on Home")
        assertTrue(waitForRow(list, own), "The original name should be restored, but the list shows ${list.rowNames()}")
        list.confirm()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(30)))
        assertEquals(own, home.shuttleText())
    }

    @Test
    @DisplayName("[SHUT-015] Renaming while offline does not rename: the dialog stays open and the name is unchanged once online again")
    fun renameOffline(rides: RideFixture) {
        step("Given the driver has opened the pencil of Shuttle-1")
        val list = openList(loginToHome())
        val dialog = list.openRename(own)
        val adb = Adb()
        try {
            step("When the network is off and a new name is submitted, then the dialog stays open")
            dialog.setName("$own R")
            assertTrue(adb.setOffline(true), "Could not switch the network off")
            Thread.sleep(3_000)
            Toasts.mark(DriverSession.driver)
            dialog.change()
            val message = Toasts.read(DriverSession.driver, Duration.ofSeconds(12))
            observe("offline-rename-message", message ?: "(none)")
            assertTrue(RenameShuttleDialog().isShown(Duration.ofSeconds(5)), "The dialog should stay open when the rename fails")
        } finally {
            adb.setOffline(false)
        }
        step("And after the network returns and the dialog is cancelled, the shuttle still has its original name")
        Thread.sleep(5_000)
        RenameShuttleDialog().cancel()
        assertTrue(own in list.rowNames(), "The name should be unchanged, but the list shows ${list.rowNames()}")
    }

    private fun waitForRow(list: SelectShuttleScreen, name: String, timeout: Duration = Duration.ofSeconds(25)): Boolean {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            if (name in runCatching { list.rowNames() }.getOrDefault(emptyList())) return true
            Thread.sleep(1_000)
        }
        return false
    }

    /** Puts the shuttle's original name back, whatever state a failed test left it in. */
    private fun restoreName(list: SelectShuttleScreen, current: String) {
        runCatching {
            val names = list.rowNames()
            val renamedOne = names.firstOrNull { it != other && it != own } ?: current.takeIf { it in names } ?: return
            val dialog = list.openRename(renamedOne)
            dialog.setName(own)
            if (dialog.isChangeEnabled()) dialog.change() else dialog.cancel()
            waitForRow(list, own)
        }.onFailure { LoggerFactory.getLogger("cleanup").error("COULD NOT RESTORE the shuttle name '$own': {}", it.message) }
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
