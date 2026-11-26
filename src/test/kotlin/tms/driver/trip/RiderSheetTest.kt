package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.GoBackDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T15 (PSNG-001 to PSNG-004 of docs/next-features-plan.md): what the rider details sheet shows from each entry point (trip view, Arrived
 * screen), what PICK UP does to it, and long or special-character notes. PSNG-005 (rider label) is dropped: the Rider API cannot set one.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Rider details sheet")
class RiderSheetTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun spec(seed: Long, note: String = "Hello", room: String? = null, flight: String? = null) =
        RideSpec(partySize = 3, ada = false, note = note, seed = seed, pickupName = pickup, dropoffName = dropoff, roomNo = room, flightNo = flight)

    private fun startTrip(): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Tag("smoke")
    @Test
    @DisplayName("[PSNG-002] PICK UP in the rider sheet shows 'Picked', CONFIRM moves the trip on to the drop-off, and the Arrived screen there lists the rider under drop-offs")
    fun pickUpFromSheetCarriesToArrivedScreen(rides: RideFixture) {
        step("Given a ride for 3 guests is assigned and the trip has started")
        val ride = rides.assignedRide(spec(291))
        val trip = startTrip()

        step("When the driver opens the rider and taps PICK UP, then the sheet shows 'Picked' and offers CANCEL and CONFIRM")
        val sheet = trip.tapRider(0)
        assertEquals("PICK UP", sheet.actionText())
        assertNull(sheet.pickedLabel(), "Nobody is picked up yet")
        sheet.tapAction()
        assertEquals("Picked", sheet.pickedLabel())
        assertEquals("CONFIRM", sheet.actionText())
        assertEquals("CANCEL", sheet.cancelText())

        step("When the driver taps CONFIRM, then the trip view goes on to the drop-off with only that stop left")
        sheet.tapAction()
        assertTrue(trip.isShown(Duration.ofSeconds(20)), "The trip view should be shown again")
        assertEquals(dropoff, trip.currentStopName())
        assertEquals(1, trip.stopNames().size, "Only the drop-off is left")

        step("And at the drop-off the Arrived screen has no pick-ups, lists the rider under DROP-OFFS and offers COMPLETE TRIP")
        val arrived = trip.tapArrived()
        assertEquals(dropoff, arrived.stopName())
        assertTrue(arrived.noPickupsMessageShown(), "'No Pick-ups at this stop' should be shown")
        assertEquals(1, arrived.dropoffChips().size)
        assertEquals("COMPLETE TRIP", arrived.proceedText())
        observe("seats-occupied-at-dropoff", arrived.seatsOccupied())
        assertEquals(ride.riderName, arrived.tapRider(true).riderName())
    }

    @Test
    @DisplayName("[PSNG-003] After the pick-up the sheet shows 'Picked' with the time 'Now' and the button reads DROP OFF in the trip view; the Arrived screen's sheet has no button")
    fun sheetAfterPickUp(rides: RideFixture) {
        step("Given a ride is assigned, the trip has started and the rider has been picked up from the sheet")
        rides.assignedRide(spec(292))
        val trip = startTrip()
        trip.tapRider(0).also { it.tapAction(); it.tapAction() }
        assertTrue(trip.isShown(Duration.ofSeconds(20)))

        step("When the driver opens the rider in the trip view, then it shows 'Picked' and the time and offers DROP OFF")
        val sheet = trip.tapRider(0)
        assertEquals("Picked", sheet.pickedLabel())
        assertEquals("Now", sheet.pickedTime())
        assertEquals("DROP OFF", sheet.actionText())
        sheet.close()

        step("And on the Arrived screen the same sheet shows 'Picked' and offers no button")
        val arrived = trip.tapArrived()
        val fromArrived = arrived.tapRider(true)
        assertEquals("Picked", fromArrived.pickedLabel())
        assertFalse(fromArrived.isActionShown(), "No action is offered from the Arrived screen")
        fromArrived.close()

        step("And going back without deciding leaves the pick-up in place")
        arrived.tapBack()
        GoBackDialog().waitUntilShown().goBack()
        assertTrue(trip.waitUntilShown().isShown())
        assertEquals(1, trip.stopNames().size, "The pick-up stays done")
    }

    @Tag("smoke")
    @Test
    @DisplayName("[PSNG-004] A 300-character note with quotes, <tags>, accents and symbols is shown exactly as typed, in the trip view and on the Arrived screen, and the buttons stay reachable")
    fun specialNote(rides: RideFixture) {
        val note = "O'Brien \"VIP\" <b>bold</b> café ñ & more — " + "x".repeat(300 - 42)
        step("Given a ride with that note is assigned and the trip has started")
        rides.assignedRide(spec(293, note = note))
        val trip = startTrip()

        step("When the driver opens the rider in the trip view, then the note is shown as typed and PICK UP and CANCEL are on screen")
        val sheet = trip.tapRider(0)
        assertEquals(note, sheet.notesText())
        assertTrue(sheet.isActionShown(), "PICK UP should be reachable")
        assertEquals("CANCEL", sheet.cancelText())
        sheet.close()

        step("And the same note is shown from the Arrived screen")
        val fromArrived = trip.tapArrived().tapRider()
        assertEquals(note, fromArrived.notesText())
        fromArrived.close()
    }

    @Test
    @Disabled("Known issue, the developer is fixing it (same cause as PAX-001): the rider sheet shows no room and flight number on the Arrived screen either, so it is not specific to the trip view. Enable when fixed.")
    @DisplayName("[PSNG-001] The rider details sheet shows the room and flight number when opened from the Arrived screen")
    fun roomAndFlightFromArrivedScreen(rides: RideFixture) {
        step("Given a ride with room 412 and flight UA1234 is assigned and the trip has started")
        rides.assignedRide(spec(294, room = "412", flight = "UA1234"))
        val trip = startTrip()

        step("When the driver arrives and opens the rider, then the sheet shows the room and the flight")
        val sheet = trip.tapArrived().tapRider()
        assertEquals("412", sheet.roomText())
        assertEquals("UA1234", sheet.flightText())
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
