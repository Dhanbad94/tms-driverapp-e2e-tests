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
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ChangeDropoffScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T12b and T12c (MAP-001, 003, 004, 005 and DROP-001 to DROP-004 of docs/additional-coverage-plan.md): the Map tab of the trip
 * view and the Change Drop-off screen. The map picture itself (markers, the Stop Info dialog behind them) is not in the accessibility
 * tree and is not tested. MAP-002 (the estimate reading "Arrived") is not tested: the app leaves for the Arrived screen at that moment.
 */
@Tag("trip")
@Tag("map")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver trip view: Map tab and Change Drop-off")
class MapAndDropoffTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun startTrip(rides: RideFixture, seed: Long): Pair<TestRide, TripViewScreen> {
        val ride = rides.assignedRide(RideSpec(partySize = 4, ada = true, note = "Hello", seed = seed, pickupName = pickup, dropoffName = dropoff))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return ride to TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Test
    @DisplayName("[MAP-001 / MAP-004] The Map tab shows the destination card, and I've Arrived on it opens the Arrived screen at once")
    fun mapCard(rides: RideFixture) {
        step("Given a trip has started")
        val (ride, trip) = startTrip(rides, 221)

        step("When the driver opens MAP VIEW, then the card shows the stop, its address, an estimate in hours and I've Arrived, and the list cards are gone")
        trip.openMap()
        assertTrue(trip.isMapShown())
        assertEquals(pickup, trip.mapDestinationName())
        assertEquals(ride.request.pickup.address, trip.mapAddress())
        assertTrue(Regex("\\d+ hr( \\d+ min)?").matches(trip.mapEstimatedTime()), "Estimate was '${trip.mapEstimatedTime()}'")
        assertEquals("I've Arrived", trip.arrivedButtonText())
        assertTrue(trip.stopNames().isEmpty(), "The list cards should not be shown on the map tab")

        step("And the card lists the rider with 4 guests")
        assertEquals(listOf(4), trip.mapRiderChips().map { it.partySize })

        step("When the driver taps I've Arrived on the map, then the Arrived screen for the pickup opens, with no confirmation")
        val arrived = trip.tapArrived()
        assertEquals(pickup, arrived.stopName())
        assertEquals("Arrived At", arrived.subtitleText())
    }

    @Test
    @DisplayName("[MAP-003] Tapping the rider on the Map tab opens the rider sheet")
    fun mapRiderChip(rides: RideFixture) {
        step("Given a trip has started and the driver is on MAP VIEW")
        val (ride, trip) = startTrip(rides, 222)
        trip.openMap()

        step("When the driver taps the rider on the card, then the sheet shows the name, 4 guests, the stops, the note and PICK UP")
        val sheet = trip.tapMapRider()
        assertEquals(ride.riderName, sheet.riderName())
        assertEquals("4", sheet.partySize())
        assertEquals(pickup, sheet.pickupStop())
        assertEquals(dropoff, sheet.dropoffStop())
        assertEquals("Hello", sheet.notesText())
        assertEquals("PICK UP", sheet.actionText())
        sheet.cancel()
        assertTrue(trip.isMapShown(), "Closing the sheet leaves the Map tab as it was")
    }

    @Test
    @DisplayName("[MAP-005] Dragging the map shows RE-CENTER, and tapping it hides the button again")
    fun recenter(rides: RideFixture) {
        step("Given a trip has started and the driver is on MAP VIEW")
        val (_, trip) = startTrip(rides, 223)
        trip.openMap()
        assertFalse(trip.isRecenterShown(), "RE-CENTER should not be there before the map is moved")

        step("When the driver drags the map, then RE-CENTER appears")
        trip.dragMap()
        assertTrue(trip.isRecenterShown(), "RE-CENTER should appear after the map is moved")
        assertEquals("RE-CENTER", trip.recenterText())

        step("And tapping it hides the button again")
        trip.tapRecenter()
        assertTrue(trip.waitUntilRecenterGone(), "RE-CENTER should go once the map is centred again")
    }

    @Test
    @DisplayName("[DROP-001 / DROP-002] Change Drop-off opens with UPDATE off, says 'No match found' for a bad search, and turns UPDATE on once a stop is chosen")
    fun updateRules(rides: RideFixture) {
        step("Given a trip has started and the driver opens Change Drop-off from the rider's menu")
        val (_, trip) = startTrip(rides, 224)
        val change = trip.tapRider(0).openMenu().changeDropoff()

        step("Then UPDATE is off")
        assertFalse(change.isUpdateEnabled(), "Nothing is chosen yet, so UPDATE should be off")

        step("When a search matches nothing, then 'No match found' is shown and UPDATE stays off")
        assertTrue(change.search("zzqq").isEmpty())
        assertTrue(change.noMatchShown(), "'No match found' should be shown")

        step("When the search is cleared and a stop is chosen, then UPDATE is on")
        val offered = change.search("Mannheim")
        assertTrue("Mannheim Rd" in offered, "Mannheim Rd should be offered, but $offered was")
        change.choose("Mannheim Rd")
        assertTrue(change.isUpdateEnabled(), "UPDATE should be on once a stop is chosen")

        step("And CANCEL returns to the trip view with the drop-off unchanged")
        change.cancel()
        assertTrue(trip.isShown(Duration.ofSeconds(15)))
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
    }

    @Test
    @DisplayName("[DROP-003] Opening Change Drop-off without a network drops the driver on Home, with only the offline banner as explanation")
    fun offlineOpen(rides: RideFixture) {
        step("Given a trip has started")
        val (_, trip) = startTrip(rides, 225)
        val adb = Adb()
        try {
            step("When the network is off and the driver opens Change Drop-off")
            assertTrue(adb.setOffline(true), "Could not switch the network off")
            Thread.sleep(4_000)
            Toasts.mark(DriverSession.driver)
            runCatching { trip.tapRider(0).openMenu().changeDropoff() }

            step("Then the driver ends up on Home (Current Trip, CONTINUE TRIP) with the offline banner and no other message")
            val home = OdHomeScreen()
            assertTrue(home.waitForTrip(Duration.ofSeconds(40)), "Home should be shown")
            assertEquals("Current Trip", home.summaryText())
            assertTrue(home.startButtonText().equals("continue trip", ignoreCase = true), "Button was '${home.startButtonText()}'")
            assertTrue(tms.driver.screens.shell.ConnectivityBanner().isShown(), "The offline banner should be shown")
            val toast = Toasts.read(DriverSession.driver, Duration.ofSeconds(3))
            observe("offline-open-toast", toast ?: "(none)")
            assertEquals(null, toast, "The app gives no message of its own")
        } finally {
            adb.setOffline(false)
        }
    }

    @Test
    @DisplayName("[DROP-004] Pressing UPDATE without a network changes nothing and gives no message (recorded)")
    fun offlineUpdate(rides: RideFixture) {
        step("Given a trip has started and a new drop-off has been chosen on Change Drop-off")
        val (_, trip) = startTrip(rides, 226)
        val change = trip.tapRider(0).openMenu().changeDropoff()
        assertTrue("Mannheim Rd" in change.search("Mannheim"))
        change.choose("Mannheim Rd")
        assertTrue(change.isUpdateEnabled())
        val adb = Adb()
        try {
            step("When the network goes off and the driver presses UPDATE")
            assertTrue(adb.setOffline(true), "Could not switch the network off")
            Thread.sleep(4_000)
            Toasts.mark(DriverSession.driver)
            change.update()
            Thread.sleep(8_000)
            val toast = Toasts.read(DriverSession.driver, Duration.ofSeconds(3))
            val texts = Regex("""\btext="([^"]{2,60})"""").findAll(DriverSession.driver.pageSource).map { it.groupValues[1] }.toSet()
            observe("offline-update", "changeDropoff=${ChangeDropoffScreen().isShown(Duration.ofSeconds(1))} toast=${toast ?: "(none)"} texts=$texts")
            assertTrue(ChangeDropoffScreen().isShown(Duration.ofSeconds(2)), "The driver should stay on Change Drop-off")
            assertEquals(null, toast, "The app gives no message of its own")
            assertTrue("You are Offline" in texts, "The offline banner should be shown")
        } finally {
            adb.setOffline(false)
        }
        step("Then, once the network is back, CANCEL returns to the trip view and the trip still ends at the old drop-off")
        Thread.sleep(6_000)
        change.cancel()
        assertTrue(trip.isShown(Duration.ofSeconds(20)))
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
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
