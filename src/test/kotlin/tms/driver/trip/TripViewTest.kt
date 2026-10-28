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
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TestRide
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ArrivedScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T3: the trip view and arriving at the pickup (TRIPV-001 to TRIPV-007 and TRIPV-011 of
 * docs/trip-lifecycle-test-plan.md). Each test assigns one ride before the app login and the fixture cleans up afterwards.
 * Not covered here: "Go here instead" (it does not appear with a single ride; it needs a second ride added after the
 * trip has started, see TRIPV-010 in Phase T7) and automatic arrival (Phase T8).
 * The emulator is far from the Chicago stops, so estimated times read about 123 hours; only their order is asserted.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver trip view and arriving at the pickup")
class TripViewTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun startTripAndOpenTripView(ride: TestRide): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown()
    }

    /** "123 hr 56 min" or "45 min" as minutes, so two estimated times can be ordered. */
    private fun minutes(eta: String): Int {
        val hours = Regex("(\\d+)\\s*hr").find(eta)?.groupValues?.get(1)?.toInt() ?: 0
        val mins = Regex("(\\d+)\\s*min").find(eta)?.groupValues?.get(1)?.toInt() ?: 0
        return hours * 60 + mins
    }

    @Test
    @DisplayName("[TRIPV-001 / 002 / 003 / 004 / 005 / 011] The trip view lists the pickup and drop-off with their riders, switches between list and map, refreshes and shows rider details")
    fun tripViewContent(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff for 5 guests with ADA and the note 'Hello' is assigned, and the driver has started the trip")
        val ride = rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 41, pickupName = pickup, dropoffName = dropoff))
        val trip = startTripAndOpenTripView(ride)

        step("Then the screen is titled 'Driving to' and shows the two tabs and the I've Arrived button")
        assertEquals("Driving to", trip.titleText())
        assertEquals("LIST VIEW", trip.listTabText())
        assertEquals("MAP VIEW", trip.mapTabText())
        assertEquals("I've Arrived", trip.arrivedButtonText())

        step("And the list has a card for the pickup first and the drop-off second, each with its address and an estimated time")
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
        assertEquals(ride.request.pickup.address, trip.stopAddresses()[0])
        assertEquals(ride.request.dropoff.address, trip.stopAddresses()[1])
        assertTrue(trip.waitForEstimatedTimes(), "The estimated times should appear on both cards")
        val etas = trip.estimatedTimes()
        assertTrue(etas.size == 2 && etas.all { it.isNotBlank() }, "Estimated times: $etas")
        assertTrue(minutes(etas[1]) >= minutes(etas[0]), "The drop-off should not be sooner than the pickup: $etas")

        step("And each card shows the rider's chip with the party size of 5")
        val chips = trip.riderChipsPerStop()
        assertEquals(2, chips.size, "One list of riders per stop card")
        chips.forEach { stop ->
            assertEquals(1, stop.size)
            // The chip shortens long names with ".." and title-cases them, so compare the visible start, ignoring case.
            val shown = stop[0].name.trimEnd('.')
            assertTrue(shown.length >= 12 && ride.riderName.startsWith(shown, ignoreCase = true), "Rider chip '${stop[0].name}' should start the name '${ride.riderName}'")
            assertEquals(5, stop[0].partySize)
        }

        step("When the driver pulls down to refresh, then the same two stops are still listed")
        trip.pullToRefresh()
        Thread.sleep(3_000)
        assertEquals(listOf(pickup, dropoff), trip.stopNames())

        step("When the driver opens MAP VIEW, then the map shows the pickup as the destination with I've Arrived, and the list cards are gone")
        trip.openMap()
        assertTrue(trip.isMapShown(), "The map card should be shown")
        assertEquals(pickup, trip.mapDestinationName())
        assertEquals("I've Arrived", trip.arrivedButtonText())
        assertTrue(trip.stopNames().isEmpty(), "The list cards should not be shown on the map tab")

        step("And going back to LIST VIEW shows both stops again")
        trip.openList()
        assertEquals(listOf(pickup, dropoff), trip.stopNames())

        step("When the driver taps the rider, then the details sheet shows the name as typed, 5 guests, both stops, the note and the PICK UP button")
        val sheet = trip.tapRider(0)
        assertEquals(ride.riderName, sheet.riderName())
        assertEquals("5", sheet.partySize())
        assertEquals(pickup, sheet.pickupStop())
        assertEquals(dropoff, sheet.dropoffStop())
        assertEquals("Hello", sheet.notesText())
        assertEquals("CANCEL", sheet.cancelText())
        assertEquals("PICK UP", sheet.actionText())

        step("And CANCEL closes the sheet and leaves the trip view as it was")
        sheet.cancel()
        assertFalse(sheet.isShown(Duration.ofSeconds(3)), "The sheet should close")
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
    }

    @Test
    @DisplayName("[TRIPV-006 / 007] I've Arrived opens one 'Arrived At' screen for the pickup, and Back returns to the trip view and then Home")
    fun arriveAtThePickup(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff is assigned and the driver has started the trip")
        val ride = rides.assignedRide(RideSpec(partySize = 5, ada = true, seed = 42, pickupName = pickup, dropoffName = dropoff))
        val trip = startTripAndOpenTripView(ride)

        step("When the driver taps I've Arrived twice in quick succession")
        val arrived = trip.doubleTapArrived()

        step("Then one screen opens for the pickup, 'Arrived At $pickup', with the PICKED UP and NO SHOW headers, no seats taken yet, and BACK and GO TO NEXT STOP")
        assertEquals(pickup, arrived.stopName())
        assertEquals("Arrived At", arrived.subtitleText())
        assertEquals("PICKED UP", arrived.pickedUpHeader())
        assertEquals("NO SHOW", arrived.noShowHeader())
        assertEquals("Seats Occupied", arrived.seatsLabelText())
        assertEquals("0", arrived.seatsOccupied())
        assertEquals("/ 15", arrived.seatsTotal())
        assertEquals("BACK", arrived.backText())
        assertEquals("GO TO NEXT STOP", arrived.proceedText())

        step("When the driver goes Back once, then the trip view is shown (so only one arrived screen was opened)")
        DriverSession.driver.navigate().back()
        assertTrue(trip.isShown(Duration.ofSeconds(20)), "The trip view should be shown after one Back")
        assertFalse(ArrivedScreen().isShown(Duration.ofSeconds(2)), "No second arrived screen should be left behind")

        step("And going Back again returns to Home, which shows the 'Current Trip' with 2 stops to go and CONTINUE TRIP")
        DriverSession.driver.navigate().back()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(20)))
        assertEquals("Current Trip", home.summaryText())
        assertEquals("2 Stops to go", home.stopsText())
        assertTrue(home.startButtonText().equals("continue trip", ignoreCase = true), "Button text was '${home.startButtonText()}'")
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
